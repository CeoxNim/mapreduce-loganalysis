package com.example;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

/**
 * maoruduce实现日志分析
 * @author CeoxNim
 */
public class LogAnalysis2 {
    
    // 自定义map处理类
    public static class MyMap extends Mapper<LongWritable, Text, Text, Text>{
    
        private Text outKey = new Text();
        private Text outValue = new Text();

        // 检查时间是否符合查询要求
        public boolean time_check(String dateTime, Long limit) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            String nowTimeStr = sdf.format(System.currentTimeMillis());
            // 每天毫秒数
            long nd = 1000 * 24 * 60 * 60;
            long day = 0;
            try {
                Date nowDate = sdf.parse(nowTimeStr);
                Date date = sdf.parse(dateTime);
                day = (nowDate.getTime() - date.getTime()) / nd;
            } catch (ParseException e) {
                e.printStackTrace();
            }
            return day < limit;
        }
        
        // 重写父类的map方法，自定义处理
        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            // 获取到每一行的值，以制表符或空格隔开
            String inValue = value.toString();
            // 将每一个单词取出,获得数组. (时间年月日， 时间时分秒， 用户名， 操作名)
            String[] arr = inValue.split(" ");
            // 判断当前时间是否在30天之内
            if (!time_check(arr[0] + " " + arr[1], (long) 30)) return;
            // 设置传递给reduce的值
            outKey.set(arr[2]);
            outValue.set(arr[3]);
            // 使用上下文context传递参数
            context.write(outKey, outValue);
        }
    }

    //自定义reduce合并类
    public static class MyReduce extends Reducer<Text, Text, Text, IntWritable>{
    
        private IntWritable outValue = new IntWritable();

        //重写父类reduce方法，自动处理
        @Override
        public void reduce(Text key, Iterable<Text> value, Context context) throws IOException, InterruptedException {
            //map传递的值经过shuffle排序和重组，将相同的键合并，值为一个数组
            ArrayList<String> arr = new ArrayList<>();
            for (Text inValue_text : value){
                String inValue = inValue_text.toString();
                arr.add(inValue);
            }
            // outKey为“用户名+操作名”  outValue为“用户执行操作的次数”
            Collections.sort(arr);
            arr.add(" ");
            int cnt = 1;
            for (int i = 0; i < arr.size() - 1; i++) {
                if (!arr.get(i).equals(arr.get(i + 1))) {
                    Text outKey = new Text(key.toString() + " " + arr.get(i));
                    outValue.set(cnt);
                    context.write(outKey, outValue);
                    cnt = 1;
                }
                else cnt += 1;
            }
        }
    }

    //Driver
    public int run(Path inPath, Path outPath) throws Exception {

        //加载hdfs的配置文件
        Configuration config = new Configuration();
        //获取到job的对象
        Job job = Job.getInstance(config, LogAnalysis2.class.getSimpleName());
        //运行的类
        job.setJarByClass(LogAnalysis2.class);
        //添加输入输出路径
        FileInputFormat.addInputPath(job, inPath);
        FileOutputFormat.setOutputPath(job, outPath);
        //设置map
        job.setMapperClass(MyMap.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        //设置reduce
        job.setReducerClass(MyReduce.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);
        //提交
        boolean isSuccess = job.waitForCompletion(true);
        return isSuccess ? 0 : 1;
    }

    public static void main(String[] args) throws Exception {

        // 输入输出文件路径
        Path inPath = new Path("/data1/cuixuanning/demo/src/main/java/com/example/in.txt");
        Path outPath = new Path ("/data1/cuixuanning/demo/src/main/java/com/example/out2.txt");

        int state = new LogAnalysis2().run(inPath, outPath);
        //执行成功就退出程序
        System.exit(state);
    }
}
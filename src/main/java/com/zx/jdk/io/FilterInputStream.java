package com.zx.jdk.io;
import java.io.IOException;
import java.io.InputStream;
//装饰者模式 装饰者超类
public class FilterInputStream extends InputStream {
    //组合了一个 被装饰者
    protected volatile InputStream in;
    //创建时传入一个 被装饰者
    protected FilterInputStream(InputStream in) {
        this.in = in;
    }
    /**
     * 调用组合的 被装饰者的方法，完成对应方法
     */
    //读取对单个字节(返回的是int，可以用(byte)int转为byte)
    public int read() throws IOException {
        return in.read();
    }
    //读取到指定数组
    public int read(byte b[]) throws IOException {
        return read(b, 0, b.length);
    }
    //读取到指定数组，指定偏移量(针对数组的)和要读取的长度
    public int read(byte b[], int off, int len) throws IOException {
        return in.read(b, off, len);
    }
    //跳过若干个字节(一般的实现就是，读取后抛弃，或者改变索引)
    public long skip(long n) throws IOException {
        return in.skip(n);
    }
    //返回流剩余可读字节数
    public int available() throws IOException {
        return in.available();
    }
    //关闭流
    public void close() throws IOException {
        in.close();
    }
    //标记流的某个位置索引
    public synchronized void mark(int readlimit) {
        in.mark(readlimit);
    }
    //回滚到标记位置
    public synchronized void reset() throws IOException {
        in.reset();
    }
    //判断流是否支持mark()、reset()方法
    public boolean markSupported() {
        return in.markSupported();
    }
}

package com.zx.jdk.io;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Vector;
/**
 * 一个有序的输入流集合，从第一个输入流开始，依次往后读取，一直到最后一个流的末尾
 * 可以通过该类，对多个流进行顺序的读取，直到读取完所有的流
 */
public class SequenceInputStream extends InputStream {
    //保存输入流的集合
    Enumeration<? extends InputStream> e;
    //输入流，是集合中当前正在读取的那个输入流
    InputStream in;

    //传入集合，构建输入流
    public SequenceInputStream(Enumeration<? extends InputStream> e) {
        //赋值
        this.e = e;
        try {
            //调用该方法，获取下一个流，也就是将in设置为集合中的下一个（第一个）输入流
            nextStream();
        } catch (IOException ex) {
            // This should never happen  这应该永远不会发生
            throw new Error("panic");
        }
    }

    //使用两个流构造该对象
    public SequenceInputStream(InputStream s1, InputStream s2) {
        /**
         * 将两个流组装成集合
         */
        Vector<InputStream> v = new Vector<>(2);
        v.addElement(s1);
        v.addElement(s2);
        e = v.elements();

        try {
            //调用该方法，获取下一个流，也就是将in设置为集合中的下一个（第一个）输入流
            nextStream();
        } catch (IOException ex) {
            // This should never happen 这应该永远不会发生
            throw new Error("panic");
        }
    }

    /**
     *  如果到达末尾(EOF,文件结束符)，继续读取下一个流
     */
    final void nextStream() throws IOException {
        //关闭前一个流
        if (in != null) {
            in.close();
        }
        //判断集合中是否有更多的流
        if (e.hasMoreElements()) {
            //将in设置为下一个元素(输入流)
            in = (InputStream) e.nextElement();
            //如果流为空，抛出异常
            if (in == null)
                throw new NullPointerException();
        }
        //如果没有了，则将in设置为null
        else in = null;

    }

    //返回剩余可读字节数
    public int available() throws IOException {
        //如果没有流了，则返回0
        if (in == null) {
            return 0; // no way to signal EOF from available()
        }
        //使用流自己的方法返回
        return in.available();
    }

    //读取当前的流
    public int read() throws IOException {
        //只有当前读取的流不为空
        while (in != null) {
            //才进行读取
            int c = in.read();
            //并判断读取到的是不是-1
            if (c != -1) {
                //如果不是则直接返回读取到的int
                return c;
            }
            //如果是，则表示当前流读取完了，读取下一个流
            nextStream();
        }
        //如果当前流为空，也返回-1
        return -1;
    }
    //读取
    public int read(byte b[], int off, int len) throws IOException {
        //如果当前输入流为空，返回-1
        if (in == null) {
            return -1;
        //如果数组为空，抛出异常
        } else if (b == null) {
            throw new NullPointerException();
        //如果off或len不符合要求，抛出异常
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        //len不能为0
        } else if (len == 0) {
            return 0;
        }
        //如果有可用的流，就一直循环，直到读取到下一数据
        do {
            //调用流自己的读取方法
            int n = in.read(b, off, len);
            //如果读取到了字节
            if (n > 0) {
                //直接返回
                return n;
            }
            //否则表示当前读取的流已经读完，读取下一个流
            nextStream();
        } while (in != null);
        //如果没有可读流了，返回-1
        return -1;
    }

    //将集合中所有流都关闭
    public void close() throws IOException {
        //循环关闭所有流，知道in为空，也就是集合中没有可用流了
        do {
            nextStream();
        } while (in != null);
    }
}

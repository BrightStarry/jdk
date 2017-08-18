package com.zx.jdk.io;
import java.io.Closeable;
import java.io.IOException;
//输入流超类-装饰者模式超类 ,实现可关闭接口
public abstract class InputStream implements Closeable {
    //最大可跳过缓冲数组大小
    private static final int MAX_SKIP_BUFFER_SIZE = 2048;
    //抽象读取方法,每次调用，往后读取一个字节,返回的是int，(byte)int，即可转为字节，如果为-1，表示没有可读字节了
    public abstract int read() throws IOException;
    //读取方法 ,读取到b[]数组中
    public int read(byte b[]) throws IOException {
        //调用重载的方法，读取传入 字节数组大小的字节 无偏移的读取
        return read(b, 0, b.length);
    }
    /**
     * 让b[]偏移off个字节读取len长度的字节到b[]数组,
     * 注意，此处的偏移并非要读取的in中的偏移
     * 而是读取出字节后，从b[]数组的那个位置开始放起，也就是让b[]数组偏移off个字节
     */
    public int read(byte b[], int off, int len) throws IOException {
        //如果存储读取到的字节的字节数组b[]为空，抛出异常
        if (b == null) {
            throw new NullPointerException();
            //如果 off 或 len 不符合规范，过大或过小，则抛出下标超出范围异常
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
            //如果要读取的长度为0，则返回0
        } else if (len == 0) {
            return 0;
        }
        //调用读取方法()进行读取，c表示读取到的字节
        int c = read();
        //如果读取到的字节数为-1，表示没有可读字节，则返回-1，同样告知调用者，没有可读字节
        if (c == -1) {
            return -1;
        }
        //将读取到的字节从b[]数组偏移的下标处开始存入
        b[off] = (byte)c;
        //因为上面调用了一次read(),所以设置i为1，表示读取了1个字节
        int i = 1;
        try {
            //循环读取，直到 i(读取了的字节) 不小于 len(要读取的字节)
            for (; i < len ; i++) {
                //读取下一个字节
                c = read();
                //一旦为-1，表示没读取到，跳出循环
                if (c == -1) {
                    break;
                }
                //读取到了，就转为字节，然后存入b[]数组
                b[off + i] = (byte)c;
            }
        } catch (IOException ee) {
        }
        //返回读取到了的字节数
        return i;
    }
   //跳过n个字节,所谓的跳过也就是读取后抛弃
    public long skip(long n) throws IOException {
        //复制 跳过字节大小 到 剩余要跳过字节大小
        long remaining = n;
        int nr;
        //如果要跳过的字节小于0，直接返回0，表示跳过了0个字节
        if (n <= 0) {
            return 0;
        }
        //从 要跳过的字节数 和 最大允许跳过的字节数 中取最小值
        int size = (int)Math.min(MAX_SKIP_BUFFER_SIZE, remaining);
        //创建临时的要跳过字节大小的字节数组
        byte[] skipBuffer = new byte[size];
        //如果 剩余要跳过字节大小 > 0，继续循环
        while (remaining > 0) {
            /**
             * A:如果 要跳过的字节数 小于 最大允许跳过数，则直接读取 要 要跳过的字节数 大小的字节到临时数组
             * B:如果 要跳过的字节数 大于 最大允许跳过数，则每次读取 最大允许跳过数 大小的字节到临时数组，循环，直到读取完成
             */
            nr = read(skipBuffer, 0, (int)Math.min(size, remaining));
            /**
             * 如果read()返回值小于0,表示没有可读字节，直接跳出循环
             * 该情况出现在，当要跳过的字节大小 大于 输入流剩余字节大小的时候
             */
            if (nr < 0) {
                break;
            }
            /**
             * 如果是A，则相减后==0，相当于只执行一次循环
             * 如果是B，则，每次读取 最大允许跳过数 大小的字节，循环读取，直到读取/跳过到指定大小的字节数
             */
            remaining -= nr;
        }
        //返回跳过了多少个字节数
        return n - remaining;
    }
   //返回可读字节数
    public int available() throws IOException {
        return 0;
    }
   //关闭-未实现
    public void close() throws IOException {}
    //标记该位置，以便使用reset()方法回滚回该位置
    public synchronized void mark(int readlimit) {}
    //回滚回mark()标记的位置
    public synchronized void reset() throws IOException {
        throw new IOException("mark/reset not supported");
    }
    //判断是否支持mark()、reset()方法
    public boolean markSupported() {
        return false;
    }

}

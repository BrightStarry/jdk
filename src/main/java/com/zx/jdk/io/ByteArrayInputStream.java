package com.zx.jdk.io;
import java.io.IOException;
/**
 * 字节数组输入流 - 处理流(被装饰者)
 * 该类是在构造时传入一个byte[]，该数组在该类中就是流，而非缓存数组，所有的读取操作都是直接从该数组中读取
 */
public class ByteArrayInputStream extends InputStream {
    //该类创建时传入的数组
    protected byte buf[];
    //从buf[]中读取的下一个字节的索引，不能为负数，不能大于count;buf[pos]是要读取的下一个字节,
    protected int pos;
    //流中，当前标记的位置；如果构造函数中传入了off(偏移量)，则为偏移量，否则为0，
    protected int mark = 0;
    //索引，大于buf[]数组中的最后一个有效字符（可读取的字符数）的位置;非负数，小于buf[]长度;
    // buf[0]-buf[count-1]是可以从流中读取的字节
    //count可以理解为，最大允许读取的字节数
    protected int count;
    /**
     * 传入一个字节数组作为缓冲数组,缓冲数组不被复制(不明白这句什么意思)
     * pos，也就是开始读取的位置,默认为0; count，也就是比有效字符(可读取字符)大的索引，默认为数组长度
     */
    public ByteArrayInputStream(byte buf[]) {
        this.buf = buf;
        this.pos = 0;
        this.count = buf.length;
    }
    /**
     * 使用传入的数组作为缓冲数组，
     * pos（下一个读取位置）的初始值是offset（偏移量），表示从数组的该位置开始往后读取
     * count的初始值是从 数组长度 或 偏移量+允许读取长度(length) 中取小的那个
     * mark的初始值是偏移量
     */
    public ByteArrayInputStream(byte buf[], int offset, int length) {
        this.buf = buf;
        this.pos = offset;
        this.count = Math.min(offset + length, buf.length);
        this.mark = offset;
    }
    /**同步
     * 从流中读取下一个字节，该字节值是int类型，范围在0-255之间(因为一个byte是8bit，2^8次方，256)
     * 如果没有可读字节，返回-1;
     * 该方法不是阻塞的
     */
    public synchronized int read() {
        /**
         * &：按位与 ，同时为1，才为1，否则为0
         * 0x：表示16进制；
         * 0xFF:表示255
         * << : 右移，右补0
         * >> : 有符号右移，左补符号位；即如果符号位是1，就补1，如果符号位是0，就补0
         * >>>:无符号右移，也就是左补0
         */
        /**
         * 如果 下个要读取的索引(pos) 小于 最大允许读取的字节索引(count) ,则返回数组中pos位置的字节；否则（也就是没有可读字节了）返回-1
         * &0xff是为了byte转int时不出错,具体先不看了
         */
        return (pos < count) ? (buf[pos++] & 0xff) : -1;
    }
    /**
     * 从流中读取len长度的字节到b[]数组中，并存入b[]数组中偏移off长度的位置
     */
    public synchronized int read(byte b[], int off, int len) {
        //如果b[]数组为空，抛出异常
        if (b == null) {
            throw new NullPointerException();
            //如果 off 或 len 不符合规范，过大或过小，则抛出下标超出范围异常
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        //如果没有可读取字节，返回-1
        if (pos >= count) {
            return -1;
        }
        //avail表示 剩余可读字节数
        int avail = count - pos;
        //如果要读取的长度(len) 大于 剩余可读，则把要去读的长度 置为 剩余可读长度
        if (len > avail) {
            len = avail;
        }
        //且不允许要读取的长度小于0
        if (len <= 0) {
            return 0;
        }
        /**
         * arraycopy()方法:将buf[]数组从pos位置开始，复制到b[]数组从off位置开始的地方，共复制len个元素
         * 此处也就是读取buf[]数组中的字节到b[]数组
         */
        System.arraycopy(buf, pos, b, off, len);
        //然后将 当前读取到的字节索引 + arraycopy拷贝过去的字节长度
        pos += len;
        //返回读取了的字节长度
        return len;
    }
    /**同步
     * 跳过n个长度的字节，
     */
    public synchronized long skip(long n) {
        //计算还有多少可读字节
        long k = count - pos;
        //如果 要跳过的字节数 < 可读字节数
        if (n < k) {
            //则跳过k个字节的数(做了个>0的限制)
            k = n < 0 ? 0 : n;
        }
        //跳过k长度的字节数
        pos += k;
        //返回跳过的字节长度
        return k;
    }
    /**
     * 返回可读的字节长度，也就是允许读的长度(count)  -  当前读取到的位置(pos)
     */
    public synchronized int available() {
        return count - pos;
    }
    /**
     * 返回该流是否支持 mark()、reset()方法，因为支持，所以直接返回true
     */
    public boolean markSupported() {
        return true;
    }
    /**
     * 标记当前正在读取的位置，以便使用reset()返回该位置
     * 也就是将mark索引 = pos(当前读取的位置)
     */
    public void mark(int readAheadLimit) {
        mark = pos;
    }
    /**
     * 让pos返回mark的位置，如果没有调用过mark()方法，mark默认为0，或off(如果构造时传入了off)
     */
    public synchronized void reset() {
        pos = mark;
    }
    //关闭方法-没有实现，也无需实现，毕竟只是从该类的一个字节数组中读取字节返回
    public void close() throws IOException {
    }

}

### JDK源码学习-2017年6月15日 15:38:30
#### 首先从io、lang、util三个包入手
markdown   换行，在行末输入两个空格，然后回车

1. io包
~~~ 
1. 首先 import java.io.*; 然后可以通过ctrl+左键点击io，打开io下的类列表。


# 杂项
* Bits【二进制工具类】(工具类): 将byte[]转成基本类型的一些方法。
* FileDescriptor【文件描述类】(用来表示开放文件，开放socket(套接字)等，不透明的，被FileInputStream或FileOutputStream包含为属性)：
    * 若干属性：fd(int)、handle(long)、parent(Closeable)、otherParents(List<Closeable>)、closed(boolean)
    * 若干属性：FileDescriptor类型的in、out和err
    * closeAll()方法，关闭一个Closeable类的属性和List<Closeable>类型的属性，不同于普通的try-catch；
        在foreach关闭List<Closeanle>的每个元素的时候，在循环内部catch，如果捕捉到异常，使用Throwable的addSuppressed()方法(详见http://www.open-open.com/lib/view/open1412644695687.html)
        将异常放入压制异常队列，防止中间产生的异常丢失；到最后统一抛出。
    * attach(Closeable c)方法，增加一个流，指向这个文件描述类；如果parent属性为空，则parent=c；如果parent不为空，则加入到otherParents列表中。
    * PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out));可以输出对应的字符，不过一般用System.out.println()代替。
    作用：(不确定)用来屏蔽不同平台间的输出输入差异用的，例如System.out.Println("1");在不同的操作系统，或者是Eclipse上，输出的都是1；可能是对每个平台底层的调用，也就是JNI（java native interface）;
* File【文件类】(抽象的表示目录或文件的类,可以连接远程主机共享的文件；实现Serializable和Comparanle接口) ：
    * fs(FileSystem )属性,调用DefaultFileSystem.getFileSystem()；返回当前平台所属的文件系统；PathStatus(enum)属性，表示该File的path是否有效；分为INVALID和CHECKED
    * status(PathStatus)属性，将上面的PathStatus作为类型修饰，且使用transient关键字修饰,表明其在序列化时不会被保存。prefixLength(int)属性，表名pathname的前缀长度，如果没有前缀，则为0；例如.//192.168.0.1/应该为前缀
    * separatorChar(char)属性，不同操作系统的分隔符；separator(String)属性，将上面的separatorChar保存成String类型
    * pathSeparatorChar(char)和pathSeparator(string)属性，和上面的属性的区别是，这个分隔符用来分割多个不同路径，例如C:\a;F:\b，“;”就是这个分隔符，而“\”是separator；
    * isInvalid()方法，检查文件是否失效；目前检查十分有限，只判断是否包含null，如果返回true，肯定是无效的；但如果返回false，也不一定有效；
    * File(String pathname)构造方法，pathname为空，抛出空指针异常;使用FileSystem的normalize()方法将pathname格式化成标准路径；再使用它的prefixLength()方法计算该标准路径的前缀长度；都赋值给自身属性；
    * File(String parent/File parent,String child)构造方法,简单地说，就是把一个路径分开来处理；例如D:\a\b\c.txt，就可以分割为D:\a\b和c.txt或者D:\a和b\c.txt；
        用处，例如只操作D盘文件，就可以指定一个File的path为D：,然后底下所有的子File都通过传入这个File对象来构造，这样就只会操作D盘下的文件了。
        child为空时，抛出异常；parent为空时，只使用child创建File;parent为""时，使用FileSystem(继承它的子类，windows,NTF文件系统中应该是WinNTFileSystem这个类)的getDefaultParent()方法，获取默认父路径创建；
    * File(URI uri)构造方法，还可以用uri创建file；
    * getName()方法，返回最后的文件名或目录名;使用lastIndexOf()方法返回分隔符最后位置索引，如果索引小于prefixLength，就使用prefixLength变量substring到末尾,否则使用索引substring到末尾;具体还要看prefixLength的赋值方法;
    * getParent()方法，与getName()方法正好相反，使用lastIndexOf()方法返回索引后，截取首字符到索引之间的string;如果索引小于prefixLength，同样使用prefixLength；如果没有parent，返回null;
    * getParentFile()方法，调用getParent()方法，用获取到的parent创建File返回；如果parent为空，则返回null;
    上面两个方法，假使一个D:\a\b\c.txt路径，则c.txt为name，D:\a\b为parent;此外，这些方法中，有些为空直接抛出异常，有些则是返回null，需要自己注意。
    * isAbsolute()方法，判断path是否是绝对路径；Linux中判断prefix是不是/，windows中判断prefix是不是\\或\\\\，实际上是\和\\，不过因为java需要转义，所以每个\用\\表示
    * getAbsolutePath()方法，返回绝对路径;
    * toURI()方法，转为URI，如果需要转为URL，不建议使用toURL(),应该先转为URI，再使用URI.toURL()方法转为URL；
    * canRead()/canWrite()方法，返回该文件是否可读/可写;使用lang包下的SecurityManager类判断；exists()方法，返回该文件或目录是否存在；
    * isDirectory()/isFile()方法，判断是否为目录/文件；如果需要区分IOException和File不是目录/文件的异常，或者需要同个文件的多个属性，可以使用Files.readAttributes方法;
    * 其他: 是否隐藏：isHidden();最后修改时间:lastModified()；文件长度：length()；设为读写权限：setReadable/setWritable();设为只读：setReadOnly()；设置最后修改时间：setLastModified(long)；
    * createNewFile()方法,根据File创建文件，如果文件原本不存在且创建成功，返回true；否则返回false；
    * delete()方法，删除File指向的文件或目录，如果是目录，只有目录为空才可以删除，删除失败返回fasle;Files类也有个delete方法，当无法删除文件时，抛出IOException，可以诊断删除失败原因；
    * deleteOnExit()方法，在JVM退出的时候，将该File指向的文件或目录删除；一旦发出删除请求，则无法终止；
    * list()方法，如果File不表示目录，返回null，否则，返回该目录下的所有文件的文件名数组;不保证顺序;Files类有一个newDirectoryStream()方法打开目录并遍历目录中的名称，在操作大目录时可减少资源。
        list()方法可传入一个FilenameFilter接口实现类，实现accept(File,String)方法，只返回指定要求名字的文件；
        listFiles()/listFiles(FilenameFilter)/listFiles(FileFilter)方法，同上，不过直接返回File数组，就是多了一层包装成File而已。而FileFilter和FilenameFilter大同小异；
    * mkdir()/mkdirs()方法，根据File创建目录/所有目录(包括父目录)，成功返回true，否则false；
    * renameTo()方法，重命名、移动文件,就是将AFile变成BFile，成功为true，否则为false；如果需要跨平台跨文件系统，最好使用独立于平台的Files类的move()方法；
    上面的几个使用SecurityManager检查的方法，如果SecurityManager该类存在，但调用对应的check方法checkRead/checkWrite方法被拒绝时，会抛出SecurityException；
    上面的大部分方法都是通过FileSystem接口的对应方法实现的。
    最后，下面的类，方法不再这么写了，只写比较重要的以及实现比较好的，其他的查API就好了；
* FileSystem【文件系统抽象类】(该抽象类基本全是抽象方法和几个常量；不同操作系统都有不同的FileSystem子类，不同子类有对自己平台文件进行操作的具体方法)
* WinNTFileSystem【windows平台的文件系统类】(继承FileSystem,实现windows平台下)
    
     
        
    
# 输入流
* InputStream【输入流】(抽象超类，装饰者模式超类):
    * read()方法是抽线的，交由具体的子类去实现具体的读取方法；read(xx,xx,xx)方法的一些重载方法，也无非是包装下read()方法，可以读取字节到byte[],或者根据偏移量读取之类的。
        read()会返回0-255之间的int，如果到达流的末端，则返回-1；如果没有可用的字节，但未到达末端，则阻塞到有可用的。或抛出IOException
    * skip(x)方法是跳过指定大小的字节数，其原理无非是使用read()读取x大小的字节然后就相当于跳过了。
        其中，如果x过大，超过了MAX_SKIP_BUFFER_SIZE（最大允许跳过的长度，默认为2048，final），那么就调用多次read()读取。
    * available()方法，返回可用的、未读取的字节长度。在该类中，默认return 0；应该是需要子类重写，返回确切的长度。
    * close()方法，因为该类实现了Closable接口，需要实现该方法。同样没有默认实现。
    * mark(int readlimit)方法，在InputStream中标记当前位置，然后读取了一些字节，随后可以调用reset()方法回到该位置，以实现重复读取。
        readlimit参数，表明了在标记重置前，最多允许读取的字节数（JDK文档中没有说明的是，实际上，允许读取的最大值，是取readlimit和BufferedInputStream规定缓冲区大小这二者的最大值）。
        如果超过了允许读取的最大值，则mark标记无效；该方法是同步的；该方法只能在被BufferedInputStream类包装的InputStream中使用。
    * reset()方法，同步的方法。回到最后一次调用mark()的位置。
        如果markSupported()为ture;且mark()未被调用过，或者失效了，则可能抛出IOException;如果未抛出这样的异常，则表示已经退到最后一次调用mark()方法的位置，或者初始位置(从未调用mark())。
        如果markSupported()为false，如果未抛出IOException,则表示回退成功。
    * markSupported()方法，检测该InputStream是否支持mark，也就是是否使用过mark()方法，或者使用mark()方法后，是否读取超过了最大数值，导致mark失效。
        也就是说，只要该方法返回true，该InputStream就可以调用reset()回到之前的位置。
    如果子类没有重写InputStream的reset()等方法，然后调用了这些方法，默认会抛出IOException("mark/reset not supported");  
    
* FilterInputStream【过滤输入流】(继承InputStream，装饰者模式装饰者超类,且有一个被protected volatile修饰的InputStream属性)：
    * FilterInputStream(InputStream)构造方法，唯一的构造方法，且需要传入一个InputStream参数,该参数将被赋值给该类的InuputStream属性;
    * read()，调用了属性InputStream类的read(),例如传入FileInuputStream类，则调用FileInputStream类的read()方法实现。
    其他方法和read()一样，全都是调用传入的InputStream类的相应方法实现，是典型的装饰者模式。
    
# 节点流(被装饰者)
* FileInputStream【文件输入流】(继承InputStream):主要用来读取二进制文件，例如图像；如果需要读取大量字符，考虑使用FileReader
    * FileDescriptor属性,该类，上面有描述过。
    * path(String)属性，文件路径，如果是使用FileDescriptor创建的，该属性为null；
    * close()方法，该类自己设了一个Object属性closeLock来作同步代码块的锁。而且只锁了对closed(volatile boolean，判断该流是否关闭)这个属性的修改操作；
        此前，我一直无法确定多线程并发时，到底该同步哪些地方；由此看来，只需要同步可能会多个线程并发对变量修改的地方就可以了。
        该方法调用native的close0()方法，来关闭FileDescriptor中closeAll()方法；
    * FileInputStream(File file)构造方法，如果文件找不到抛出FileNotFoundException；如果被安全管理器拒绝(应该是没权限),抛出SecurityException;如果file为空或者其path为空，则抛出NullPointException；
        该方法会创建SecurityManager类，调用checkRead()方法，来验证File的安全性（是否允许读？）；然后创建一个FileDecriptor赋值给自己的属性fd，并调用fd.attach()方法，传递this过去。
        并会将File的path赋值给自己的path属性；然后调用自己的open()方法，在open(String)中调用自己的open0(String)方法，该方法是native方法，用来打开操作系统中对该文件的读操作。
    * FileInputStream(FileDescriptor fdObj)构造方法，可以让该FileInputStream和Closeable共享一个流（同一个FileDescriptor）；
    * read()方法中调用了一个native 的read0()方法，来进行读取；如果读到末尾，返回-1；
    * readBytes(byte b[], int off, int len)方法也是native；read(byte[] b)方法直接调用readBytes()；read(byte b[], int off, int len)方法也是直接调用readBytes();
    * skip(long n)方法，跳过并丢弃n长度的字节；也是native；
    * available()方法，native，返回可用的字节长度int;
    * getFD()方法，返回该类中的FileDescriptor属性，如果为空，则抛出IOException；
    * getChannel()方法，调用FileChannelImpl.open(x,x,x,x,x)方法，来创建一个nio包中的Channel返回，以便进行NIO的读操作(open()方法有几个boolean参数确定了其是否可以读写)；
        返回的FileChannel，默认的position也会是当前BIO读取到的位置；
    * initIDS()方法，native，在静态代码块中被调用；作用是获取内存地址偏移量；
    * finalize()方法，该方法是Object的protected方法，子类可以覆盖该方法以实现资源清理工作，GC在回收对象之前调用该方法；Java语言规范并不保证finalize方法会被及时地执行、而且根本不会保证它们会被执行，finalize方法可能会带来性能问题；
    
    
# 处理流(装饰者)
* BufferrdInputStream【缓存输入流】(继承FilterInputStream):包装各个节点流(被装饰者),使它们有缓冲的输入，支持了mark()和reset()方法

~~~


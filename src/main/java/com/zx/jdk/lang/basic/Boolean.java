package com.zx.jdk.lang.basic;

/**
 * 该类是boolean的包装类.该类包含了一个为boolean类型的属性.
 * <p>此外，该类提供了许多方法将boolean转为string，将string转为boolean，
 * 以及其他一些方法。
 * 实现了Comparable<Boolean>接口
 *
 * @author  Arthur van Hoff
 * @since   JDK1.0
 */
public final class Boolean implements java.io.Serializable,
        Comparable<Boolean>
{
    /**
     * 与原始boolean类型true相当的Boolean对象
     */
    public static final Boolean TRUE = new Boolean(true);

    /**
     * 与原始boolean类型false相当的Boolean对象
     */
    public static final Boolean FALSE = new Boolean(false);

    /**
     * 基本类型boolean的类对象
     */
    @SuppressWarnings("unchecked")
    public static final Class<Boolean> TYPE = (Class<Boolean>) Class.getPrimitiveClass("boolean");

    /**
     * Boolean对象中维护的boolean基本类型值
     */
    private final boolean value;

    /** 序列化id*/
    private static final long serialVersionUID = -3665804199014368530L;

    /**
     * 分配一个Boolean对象，表示value
     * <p>注意：使用这个构造函数是不合适的,
     * 除非要创建一个新的实例.
     * 静态工厂 {@link #valueOf(boolean)}方法是通常是更好的选择，能大大提高时间和空间性能
     *
     */
    public Boolean(boolean value) {
        this.value = value;
    }

    /**
     * 如果s不为空，分配一个新的Boolean表示该值,
     * 如果s是TRUE或true，则Boolean为true，其他字符Boolean都为false。
     *
     * <p>这个方法貌似有个问题，那就是还是调用上面不推荐调用的this(boolean value)方法了.
     * 底下的{@link #valueOf(String s)}方法也应该优先于这个方法
     *
     * @param   s   the string to be converted to a {@code Boolean}.
     */
    public Boolean(String s) {
        this(parseBoolean(s));
    }

    /**
     * 也就是上面方法的实现逻辑，只有 s != null ，并且s等于true(忽略大小写),才为true.
     */
    public static boolean parseBoolean(String s) {
        return ((s != null) && s.equalsIgnoreCase("true"));
    }

    /**
     * 返回Boolean对象的原始类型boolean的值
     */
    public boolean booleanValue() {
        return value;
    }

    /**
     * 返回一个Boolean对象表示指定的boolean值，
     * 如果不需要一个新的Boolean对象(程序逻辑上),那么该方法应该比this(boolean)方法优先使用.
     * <p>这个方法可以很大程度上提升空间和时间性能.
     * 因为它使用了该类维护的两个对应的Boolean属性，直接返回即可.无需创建新的对象
     */
    public static Boolean valueOf(boolean b) {
        return (b ? TRUE : FALSE);
    }

    /**
     * 将s转为Boolean类型，很简单的方法，和上面的逻辑类似,
     * 这个方法也应该优先于this(String)方法
     */
    public static Boolean valueOf(String s) {
        return parseBoolean(s) ? TRUE : FALSE;
    }

    /**
     * 也是很简单的toString方法。
     * 我想过这里是不是需要把true和false字符串也保存为常量。不过细想了下，没有必要.
     * 因为浪费这么些空间，来存储这么一个基本不怎么用得到的方法，没必要.
     */
    public static String toString(boolean b) {
        return b ? "true" : "false";
    }

    /**
     * 同上，不过不是静态方法
     */
    public String toString() {
        return value ? "true" : "false";
    }

    /**
     * 返回这个Boolean对象的hashCode
     * 这个对象如果表示了true，则为1231，如果表示了false，则为1237
     */
    @Override
    public int hashCode() {
        return Boolean.hashCode(value);
    }

    /**
     * 返回boolean值的hashCode,与{@code Boolean.hashCode()}兼容
     */
    public static int hashCode(boolean value) {
        return value ? 1231 : 1237;
    }

    /**
     * 如果参数不是空，并且和该对象的value属性相同，才为true
     */
    public boolean equals(Object obj) {
        if (obj instanceof Boolean) {
            return value == ((Boolean)obj).booleanValue();
        }
        return false;
    }

    /**
     * 只有当系统属性值为true字符串时，才为true，
     * 从JavaTM平台1.0.2版本开始，用来判断该这个字符串是不是忽略大小写的
     * <p>
     * 如果没有指定name 或者指定的属性为空，返回false
     *
     * @param   name   the system property name.
     * @return  the {@code boolean} value of the system property.
     * @throws  SecurityException for the same reasons as
     *          {@link System#getProperty(String) System.getProperty}
     * @see     java.lang.System#getProperty(java.lang.String)
     * @see     java.lang.System#getProperty(java.lang.String, java.lang.String)
     */
    public static boolean getBoolean(String name) {
        boolean result = false;
        try {
            result = parseBoolean(System.getProperty(name));
        } catch (IllegalArgumentException | NullPointerException e) {
        }
        return result;
    }

    /**
     * 比较两个Boolean值
     */
    public int compareTo(Boolean b) {
        return compare(this.value, b.value);
    }

    /**
     * 比较两个boolean值，
     * 其下代码是相同的，返回0
     * <pre>
     *    Boolean.valueOf(x).compareTo(Boolean.valueOf(y))
     * </pre>
     * 如果不同，并且x为true，则为1，如果x为false，则为-1
     */
    public static int compare(boolean x, boolean y) {
        return (x == y) ? 0 : (x ? 1 : -1);
    }

    /**
     * 返回and结果
     */
    public static boolean logicalAnd(boolean a, boolean b) {
        return a && b;
    }

    /**
     * 返回两个boolean类型的或结果
     * 或： 。。。这就不写了把
     */
    public static boolean logicalOr(boolean a, boolean b) {
        return a || b;
    }

    /**
     * 返回两个boolean类型的异或结果
     * 异或：相同为false，不同为true
     */
    public static boolean logicalXor(boolean a, boolean b) {
        return a ^ b;
    }
}

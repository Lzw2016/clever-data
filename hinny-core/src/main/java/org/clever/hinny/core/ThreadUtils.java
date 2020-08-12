package org.clever.hinny.core;

/**
 * 作者：lizw <br/>
 * 创建时间：2020/07/28 22:34 <br/>
 */
public class ThreadUtils {
    public static final ThreadUtils Instance = new ThreadUtils();

    private ThreadUtils() {
    }

    /**
     * 线程栈信息
     *
     * @return 线程栈信息字符串
     */
    public String track(Thread thread) {
        return org.clever.common.utils.ThreadUtils.track(thread);
    }

    /**
     * 当前线程栈信息
     *
     * @return 线程栈信息字符串
     */
    public String track() {
        return org.clever.common.utils.ThreadUtils.track();
    }

    /**
     * 打印线程栈信息
     */
    public void printTrack(Thread thread) {
        org.clever.common.utils.ThreadUtils.printTrack(thread);
    }

    /**
     * 打印当前线程栈信息
     */
    public void printTrack() {
        org.clever.common.utils.ThreadUtils.printTrack();
    }
}

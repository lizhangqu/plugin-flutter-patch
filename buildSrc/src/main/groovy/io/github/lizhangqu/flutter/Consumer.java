package io.github.lizhangqu.flutter;

/**
 * 转换抽象接口
 *
 * @author lizhangqu
 * @version V1.0
 * @since 2019-03-14 14:11
 */
public interface Consumer<T, U> {

    /**
     * Performs this operation on the given arguments.
     *
     * @param t the first input argument
     * @param u the second input argument
     */
    void accept(String variantName, String path, T t, U u);
}

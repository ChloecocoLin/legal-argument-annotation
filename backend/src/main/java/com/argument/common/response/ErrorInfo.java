package com.argument.common.response;

import java.util.List;

/**
 * 错误信息：code、message、字段级明细。
 */
public record ErrorInfo(String code, String message, List<FieldRule> details) {}

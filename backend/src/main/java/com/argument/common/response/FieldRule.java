package com.argument.common.response;

/**
 * 字段校验明细：出错字段与违反的规则。
 */
public record FieldRule(String field, String rule) {}

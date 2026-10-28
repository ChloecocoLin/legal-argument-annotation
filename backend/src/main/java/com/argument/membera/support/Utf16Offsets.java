package com.argument.membera.support;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** UTF-16 半开区间，以 DocumentTextVersion.normalizedText 为唯一基准。 */
public final class Utf16Offsets {

    private Utf16Offsets() {}

    public static void validate(
            String normalizedText, int scopeStart, int scopeEnd, List<Map<String, Object>> boundaries) {
        if (normalizedText == null) {
            throw new BusinessException(ErrorCode.OFFSET_INVALID, "缺少规范化文本");
        }
        int length = normalizedText.length();
        if (scopeStart < 0 || scopeEnd > length || scopeStart >= scopeEnd) {
            throw new BusinessException(ErrorCode.OFFSET_INVALID, "标注范围超出文本");
        }
        if (boundaries == null || boundaries.isEmpty()) {
            throw new BusinessException(ErrorCode.OFFSET_INVALID, "至少需要一条边界");
        }
        List<int[]> ranges = new ArrayList<>();
        for (Map<String, Object> boundary : boundaries) {
            int start = toInt(boundary.get("startOffset"));
            int end = toInt(boundary.get("endOffset"));
            if (start < scopeStart || end > scopeEnd || start >= end) {
                throw new BusinessException(ErrorCode.OFFSET_INVALID, "边界必须落在标注范围内且为半开区间");
            }
            if (splitsSurrogate(normalizedText,start) || splitsSurrogate(normalizedText,end)) {
                throw new BusinessException(ErrorCode.OFFSET_INVALID,"边界不能切开 UTF-16 代理对");
            }
            ranges.add(new int[] {start, end});
        }
        ranges.sort(Comparator.comparingInt(a -> a[0]));
        int cursor = scopeStart;
        for (int[] range : ranges) {
            if (range[0] < cursor) {
                throw new BusinessException(ErrorCode.OFFSET_INVALID, "边界重叠");
            }
            if (range[0] > cursor) {
                throw new BusinessException(ErrorCode.OFFSET_INVALID, "边界存在遗漏");
            }
            cursor = range[1];
        }
        if (cursor != scopeEnd) {
            throw new BusinessException(ErrorCode.OFFSET_INVALID, "边界未覆盖完整标注范围");
        }
    }

    public static String slice(String text, int start, int end) {
        return text.substring(start, end);
    }

    private static boolean splitsSurrogate(String text,int at){return at>0&&at<text.length()&&Character.isHighSurrogate(text.charAt(at-1))&&Character.isLowSurrogate(text.charAt(at));}

    public static List<int[]> suggest(String text, int scopeStart, int scopeEnd) {
        List<int[]> out = new ArrayList<>();
        int start = scopeStart;
        for (int i = scopeStart; i < scopeEnd; i++) {
            char ch = text.charAt(i);
            if (ch == '。' || ch == '；' || ch == ';') {
                int end = i + 1;
                if (end > start) {
                    out.add(new int[] {start, end});
                }
                start = end;
            }
        }
        if (start < scopeEnd) {
            out.add(new int[] {start, scopeEnd});
        }
        return out;
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            throw new BusinessException(ErrorCode.OFFSET_INVALID, "缺少偏移");
        }
        return Integer.parseInt(String.valueOf(value));
    }
}

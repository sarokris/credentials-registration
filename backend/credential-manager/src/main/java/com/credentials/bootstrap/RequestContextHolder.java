package com.credentials.bootstrap;

import com.credentials.dto.RequestUserContext;
import lombok.experimental.UtilityClass;

@UtilityClass
public class RequestContextHolder {

    private static final ThreadLocal<RequestUserContext> holder = new ThreadLocal<>();

    public static void set(RequestUserContext context) {
        holder.set(context);
    }

    public static RequestUserContext get() {
        return holder.get();
    }

    public static void clear() {
        holder.remove();
    }
}

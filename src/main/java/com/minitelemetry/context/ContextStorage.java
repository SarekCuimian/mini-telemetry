package com.minitelemetry.context;

public interface ContextStorage {
    Context current();

    Scope attach(Context context);
}

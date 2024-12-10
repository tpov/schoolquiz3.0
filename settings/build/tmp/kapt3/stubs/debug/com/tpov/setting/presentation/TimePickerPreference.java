package com.tpov.setting.presentation;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0006\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\b\u0000\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u001a\u0010\u000e\u001a\u0004\u0018\u00010\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u0013H\u0014J\u0012\u0010\u0014\u001a\u00020\u00152\b\u0010\u0016\u001a\u0004\u0018\u00010\u000fH\u0014R$\u0010\t\u001a\u00020\b2\u0006\u0010\u0007\u001a\u00020\b@FX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u000b\"\u0004\b\f\u0010\r\u00a8\u0006\u0017"}, d2 = {"Lcom/tpov/setting/presentation/TimePickerPreference;", "Landroidx/preference/DialogPreference;", "context", "Landroid/content/Context;", "attrs", "Landroid/util/AttributeSet;", "(Landroid/content/Context;Landroid/util/AttributeSet;)V", "value", "", "time", "getTime", "()Ljava/lang/String;", "setTime", "(Ljava/lang/String;)V", "onGetDefaultValue", "", "a", "Landroid/content/res/TypedArray;", "index", "", "onSetInitialValue", "", "defaultValue", "settings_debug"})
public final class TimePickerPreference extends androidx.preference.DialogPreference {
    @org.jetbrains.annotations.NotNull
    private java.lang.String time = "00:00";
    
    public TimePickerPreference(@org.jetbrains.annotations.NotNull
    android.content.Context context, @org.jetbrains.annotations.NotNull
    android.util.AttributeSet attrs) {
        super(null, null, 0, 0);
    }
    
    @org.jetbrains.annotations.NotNull
    public final java.lang.String getTime() {
        return null;
    }
    
    public final void setTime(@org.jetbrains.annotations.NotNull
    java.lang.String value) {
    }
    
    @java.lang.Override
    @org.jetbrains.annotations.Nullable
    protected java.lang.Object onGetDefaultValue(@org.jetbrains.annotations.NotNull
    android.content.res.TypedArray a, int index) {
        return null;
    }
    
    @java.lang.Override
    protected void onSetInitialValue(@org.jetbrains.annotations.Nullable
    java.lang.Object defaultValue) {
    }
}
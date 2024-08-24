package com.tpov.setting.presentation;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000N\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0000\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0018\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\fH\u0016J\u001c\u0010\r\u001a\u00020\b2\b\u0010\u000e\u001a\u0004\u0018\u00010\u000f2\b\u0010\u0010\u001a\u0004\u0018\u00010\u0011H\u0016J\u0010\u0010\u0012\u001a\u00020\b2\u0006\u0010\u0013\u001a\u00020\u0014H\u0016J\u0010\u0010\u0015\u001a\u00020\u00162\u0006\u0010\u0017\u001a\u00020\u0018H\u0016J\b\u0010\u0019\u001a\u00020\bH\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001a"}, d2 = {"Lcom/tpov/setting/presentation/SettingsFragment;", "Landroidx/preference/PreferenceFragmentCompat;", "()V", "preferencesManager", "Lcom/tpov/setting/data/PreferencesManager;", "settingsDomain", "Lcom/tpov/setting/domain/SettingsDomain;", "onCreateOptionsMenu", "", "menu", "Landroid/view/Menu;", "inflater", "Landroid/view/MenuInflater;", "onCreatePreferences", "savedInstanceState", "Landroid/os/Bundle;", "rootKey", "", "onDisplayPreferenceDialog", "preference", "Landroidx/preference/Preference;", "onOptionsItemSelected", "", "item", "Landroid/view/MenuItem;", "savePreferences", "settings_debug"})
public final class SettingsFragment extends androidx.preference.PreferenceFragmentCompat {
    private com.tpov.setting.data.PreferencesManager preferencesManager;
    private com.tpov.setting.domain.SettingsDomain settingsDomain;
    
    public SettingsFragment() {
        super();
    }
    
    @java.lang.Override
    public void onCreatePreferences(@org.jetbrains.annotations.Nullable
    android.os.Bundle savedInstanceState, @org.jetbrains.annotations.Nullable
    java.lang.String rootKey) {
    }
    
    @java.lang.Override
    public void onDisplayPreferenceDialog(@org.jetbrains.annotations.NotNull
    androidx.preference.Preference preference) {
    }
    
    @java.lang.Override
    public void onCreateOptionsMenu(@org.jetbrains.annotations.NotNull
    android.view.Menu menu, @org.jetbrains.annotations.NotNull
    android.view.MenuInflater inflater) {
    }
    
    @java.lang.Override
    public boolean onOptionsItemSelected(@org.jetbrains.annotations.NotNull
    android.view.MenuItem item) {
        return false;
    }
    
    private final void savePreferences() {
    }
}
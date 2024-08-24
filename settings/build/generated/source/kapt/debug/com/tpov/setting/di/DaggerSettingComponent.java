package com.tpov.setting.di;

import android.app.Application;
import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
import dagger.internal.InstanceFactory;
import dagger.internal.Preconditions;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava"
})
public final class DaggerSettingComponent {
  private DaggerSettingComponent() {
  }

  public static SettingComponent.Factory factory() {
    return new Factory();
  }

  private static final class Factory implements SettingComponent.Factory {
    @Override
    public SettingComponent create(Application application) {
      Preconditions.checkNotNull(application);
      return new SettingComponentImpl(new SettingModule(), application);
    }
  }

  private static final class SettingComponentImpl implements SettingComponent {
    private final SettingComponentImpl settingComponentImpl = this;

    private Provider<Application> applicationProvider;

    private Provider<Context> provideContextProvider;

    private SettingComponentImpl(SettingModule settingModuleParam, Application applicationParam) {

      initialize(settingModuleParam, applicationParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SettingModule settingModuleParam,
        final Application applicationParam) {
      this.applicationProvider = InstanceFactory.create(applicationParam);
      this.provideContextProvider = DoubleCheck.provider(SettingModule_ProvideContextFactory.create(settingModuleParam, applicationProvider));
    }

    @Override
    public Context provideContext() {
      return provideContextProvider.get();
    }
  }
}

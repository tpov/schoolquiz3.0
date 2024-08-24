package com.tpov.shop.di;

import android.app.Application;
import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
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
public final class ShopModule_ProvideContextFactory implements Factory<Context> {
  private final ShopModule module;

  private final Provider<Application> applicationProvider;

  public ShopModule_ProvideContextFactory(ShopModule module,
      Provider<Application> applicationProvider) {
    this.module = module;
    this.applicationProvider = applicationProvider;
  }

  @Override
  public Context get() {
    return provideContext(module, applicationProvider.get());
  }

  public static ShopModule_ProvideContextFactory create(ShopModule module,
      Provider<Application> applicationProvider) {
    return new ShopModule_ProvideContextFactory(module, applicationProvider);
  }

  public static Context provideContext(ShopModule instance, Application application) {
    return Preconditions.checkNotNullFromProvides(instance.provideContext(application));
  }
}

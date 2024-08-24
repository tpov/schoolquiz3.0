package com.tpov.shop.di;

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
public final class DaggerShopComponent {
  private DaggerShopComponent() {
  }

  public static ShopComponent.Factory factory() {
    return new Factory();
  }

  private static final class Factory implements ShopComponent.Factory {
    @Override
    public ShopComponent create(Application application) {
      Preconditions.checkNotNull(application);
      return new ShopComponentImpl(new ShopModule(), application);
    }
  }

  private static final class ShopComponentImpl implements ShopComponent {
    private final ShopComponentImpl shopComponentImpl = this;

    private Provider<Application> applicationProvider;

    private Provider<Context> provideContextProvider;

    private ShopComponentImpl(ShopModule shopModuleParam, Application applicationParam) {

      initialize(shopModuleParam, applicationParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final ShopModule shopModuleParam, final Application applicationParam) {
      this.applicationProvider = InstanceFactory.create(applicationParam);
      this.provideContextProvider = DoubleCheck.provider(ShopModule_ProvideContextFactory.create(shopModuleParam, applicationProvider));
    }

    @Override
    public Context provideContext() {
      return provideContextProvider.get();
    }
  }
}

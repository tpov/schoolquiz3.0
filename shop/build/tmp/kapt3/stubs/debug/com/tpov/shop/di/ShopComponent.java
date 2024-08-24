package com.tpov.shop.di;

@javax.inject.Singleton
@dagger.Component(modules = {com.tpov.shop.di.ShopModule.class})
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\bg\u0018\u00002\u00020\u0001:\u0001\u0004J\b\u0010\u0002\u001a\u00020\u0003H&\u00a8\u0006\u0005"}, d2 = {"Lcom/tpov/shop/di/ShopComponent;", "", "provideContext", "Landroid/content/Context;", "Factory", "shop_debug"})
public abstract interface ShopComponent {
    
    @org.jetbrains.annotations.NotNull
    public abstract android.content.Context provideContext();
    
    @dagger.Component.Factory
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\bg\u0018\u00002\u00020\u0001J\u0012\u0010\u0002\u001a\u00020\u00032\b\b\u0001\u0010\u0004\u001a\u00020\u0005H&\u00a8\u0006\u0006"}, d2 = {"Lcom/tpov/shop/di/ShopComponent$Factory;", "", "create", "Lcom/tpov/shop/di/ShopComponent;", "application", "Landroid/app/Application;", "shop_debug"})
    public static abstract interface Factory {
        
        @org.jetbrains.annotations.NotNull
        public abstract com.tpov.shop.di.ShopComponent create(@dagger.BindsInstance
        @org.jetbrains.annotations.NotNull
        android.app.Application application);
    }
}
package com.oppwa;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.oppwa.mobile.connect.checkout.dialog.CheckoutActivity;
import com.oppwa.mobile.connect.checkout.meta.CheckoutSecurityPolicyMode;
import com.oppwa.mobile.connect.checkout.meta.CheckoutSettings;
import com.oppwa.mobile.connect.checkout.meta.CheckoutSkipCVVMode;
import com.oppwa.mobile.connect.checkout.meta.CheckoutStorePaymentDetailsMode;
import com.oppwa.mobile.connect.exception.PaymentError;
import com.oppwa.mobile.connect.exception.PaymentException;
import com.oppwa.mobile.connect.payment.BrandsValidation;
import com.oppwa.mobile.connect.payment.CheckoutInfo;
import com.oppwa.mobile.connect.payment.ImagesRequest;
import com.oppwa.mobile.connect.payment.card.CardPaymentParams;
import com.oppwa.mobile.connect.provider.Connect;
import com.oppwa.mobile.connect.provider.ITransactionListener;
import com.oppwa.mobile.connect.provider.Transaction;
import com.oppwa.mobile.connect.provider.TransactionType;
import com.oppwa.mobile.connect.service.ConnectService;
import com.oppwa.mobile.connect.service.IProviderBinder;

public class RNOppwaModule extends ReactContextBaseJavaModule  {
    private final static String TAG = RNOppwaModule.class.getCanonicalName();
    private IProviderBinder binder;
    private Context mContext;
    private ReactApplicationContext mReactContext;
    private Intent bindIntent;
    private ServiceConnection serviceConnection;
    private ActivityEventListener mActivityEventListener;
    private Connect.ProviderMode providerMode = Connect.ProviderMode.TEST;

    public RNOppwaModule(ReactApplicationContext reactContext) {
        super(reactContext);
        mReactContext = reactContext;
        mContext = reactContext.getApplicationContext();
        bindIntent = new Intent(mContext, ConnectService.class);

    }

    public void unBindService() {
        if (serviceConnection != null) {
            // Unbind from the In-app Billing service when we are done
            // Otherwise, the open service connection could cause the device’s performance
            // to degrade
            mContext.unbindService(serviceConnection);
        }
    }

    private void addActivityListeners(final ReactApplicationContext reactContext, final Promise promise) {
        reactContext.removeActivityEventListener(mActivityEventListener);
        mActivityEventListener = new ActivityEventListener() {
            @Override
            public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
                reactContext.removeActivityEventListener(this);
                String resourcePath;
                if (requestCode == CheckoutActivity.CHECKOUT_ACTIVITY) {
                    switch (resultCode) {
                        case CheckoutActivity.RESULT_OK:
                            /* Transaction completed. */
                            Transaction transaction = data.getParcelableExtra(CheckoutActivity.CHECKOUT_RESULT_TRANSACTION);
                            resourcePath = data.getStringExtra(CheckoutActivity.CHECKOUT_RESULT_RESOURCE_PATH);
                            /* Check the transaction type. */
                            if (transaction.getTransactionType() == TransactionType.SYNC) {
                                /* Check the status of synchronous transaction. */
                                promise.resolve(resourcePath);
                            } else {
                                //handle async here
                                Log.d(TAG, "here");
                            }
                            break;
                        case CheckoutActivity.RESULT_CANCELED:
                            promise.reject("oppwa/cancelled", "Cancelled");
                            break;
                        case CheckoutActivity.RESULT_ERROR:
                            Bundle bundle = data.getExtras();
                            if (bundle != null) {
                                for (String key : bundle.keySet()) {
                                    Object value = bundle.get(key);
                                    Log.e(TAG, String.format("%s %s (%s)", key,
                                            value.toString(), value.getClass().getName()));
                                }
                            }
                            PaymentError error = data.getParcelableExtra(CheckoutActivity.CHECKOUT_RESULT_ERROR);
                            promise.reject("oppwa/checkout error", error.getErrorMessage());
                    }
                }
            }

            @Override
            public void onNewIntent(Intent intent) {
                if (intent.getScheme().equals("tokenize")) {
                    String checkoutId = intent.getData().getQueryParameter("id");

                    /* request payment status */
                }
            }
        };
        reactContext.addActivityEventListener(mActivityEventListener);
    }

    @Override
    public String getName() {
        return "RNOppwa";
    }

    @ReactMethod
    public void setup(ReadableMap options, final Promise promise) {
        String mode = options.getString("mode");
        if (mode.equalsIgnoreCase("live")) {
            providerMode = Connect.ProviderMode.LIVE;
        } else {
            providerMode = Connect.ProviderMode.TEST;
        }
        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                binder = (IProviderBinder) service;
                /* we have a connection to the service */
                try {
                    binder.initializeProvider(providerMode);
                    promise.resolve(null);
                } catch (PaymentException ee) {
                    promise.reject(ee);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                binder = null;
            }
        };
        mContext.startService(bindIntent);
        mContext.bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @ReactMethod
    public void isValidNumber(ReadableMap options, Promise promise) {
        if (binder == null) {
            promise.reject("oppwa/setup", "Not setup yet");
        }
        if (!CardPaymentParams.isNumberValid(options.getString("cardNumber"), options.getString("paymentBrand"))) {
            promise.reject("oppwa/card-invalid", "The card number is invalid.");
        } else {
            promise.resolve(null);
        }

    }

    /**
     * Creates the new instance of {@link CheckoutSettings}
     * to instantiate the {@link CheckoutActivity}.
     *
     * @param checkoutId the received checkout id
     * @return the new instance of {@link CheckoutSettings}
     */
    private static CheckoutSettings createCheckoutSettings(String checkoutId, String callbackScheme, Connect.ProviderMode providerMode) {
        return new CheckoutSettings(checkoutId, Constants.Config.PAYMENT_BRANDS, providerMode)
                .setWebViewEnabledFor3DSecure(true)
                .setShopperResultUrl(callbackScheme + "://callback")
                .setStorePaymentDetailsMode(CheckoutStorePaymentDetailsMode.PROMPT);
    }

    @ReactMethod
    public void tokenizeCard(ReadableMap options, Promise promise){
        try {
            /* add the activity result listener */
            addActivityListeners(mReactContext, promise);

            String checkoutID = options.getString("checkoutID");
            CheckoutSettings checkoutSettings = createCheckoutSettings(checkoutID, "tokenize", providerMode);

            /* Set up the Intent and start the checkout activity. */
            Intent intent = checkoutSettings.createCheckoutActivityIntent(mContext);
            mReactContext.startActivityForResult(intent, CheckoutActivity.REQUEST_CODE_CHECKOUT, null);
        } catch (Exception e) {
            promise.reject("oppwa/tokenizeCard", e.getMessage());
        }
    }

    @ReactMethod
    public void transactionPayment(ReadableMap options, final Promise promise) {
        if (binder == null) {
            promise.reject("oppwa/setup", "Not setup yet");
        }
        try {

            CardPaymentParams cardPaymentParams = new CardPaymentParams(options.getString("checkoutID"),
                    options.getString("paymentBrand"), options.getString("cardNumber"), options.getString("holderName"),
                    options.getString("expiryMonth"), options.getString("expiryYear"), options.getString("cvv"));

            cardPaymentParams.setTokenizationEnabled(true);
            Transaction transaction = null;

            try {

                transaction = new Transaction(cardPaymentParams);

                binder.addTransactionListener(new ITransactionListener() {
                    @Override
                    public void paymentConfigRequestFailed(PaymentError paymentError) {
                        binder.removeTransactionListener(this);
                        promise.reject("oppwa/paymentConfig", "RequestFailed " + paymentError.getErrorInfo() + " : " + paymentError.getErrorMessage());
                    }

                    @Override
                    public void transactionCompleted(Transaction transaction) {
                        binder.removeTransactionListener(this);
                        WritableMap data = Arguments.createMap();
                        data.putString("status", "transactionCompleted");
                        data.putString("checkoutID", transaction.getPaymentParams().getCheckoutId());
                        promise.resolve(data);
                    }

                    @Override
                    public void transactionFailed(Transaction transaction, PaymentError paymentError) {
                        binder.removeTransactionListener(this);

                        WritableMap data = Arguments.createMap();

                        data.putString("status", "transactionFailed");
                        data.putString("checkoutID", transaction.getPaymentParams().getCheckoutId());
                        promise.reject("oppwa/transactionFailed", "transactionFailed " + paymentError.getErrorMessage() + " : " + paymentError.getErrorInfo());
                    }

                    @Override
                    public void brandsValidationRequestSucceeded(BrandsValidation brandsValidation) {

                    }

                    @Override
                    public void brandsValidationRequestFailed(PaymentError paymentError) {

                    }

                    @Override
                    public void imagesRequestSucceeded(ImagesRequest imagesRequest) {

                    }

                    @Override
                    public void imagesRequestFailed() {

                    }

                    @Override
                    public void paymentConfigRequestSucceeded(CheckoutInfo checkoutInfo) {
                        Log.i("payment-hyperpsy", "RequestSucceeded " + checkoutInfo.getCurrencyCode());

                    }
                });
                binder.submitTransaction(transaction);
            } catch (PaymentException ee) {
                promise.reject("oppwa/transactionFailed", ee.getMessage());
            }
        } catch (PaymentException e) {
            promise.reject("oppwa/transactionFailed", e.getMessage());
        }

    }

}

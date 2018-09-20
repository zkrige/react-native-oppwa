#import "RNOppwa.h"

@implementation RNOppwa

OPPPaymentProvider *provider;

RCT_EXPORT_MODULE(RNOppwa);


/**
 * transaction
 */
RCT_EXPORT_METHOD(setup: (NSDictionary*)options resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    NSString *mode = options[@"mode"];
    mode = [mode lowercaseString];
    if ([mode isEqualToString:@"live"]) {
        provider = [OPPPaymentProvider paymentProviderWithMode:OPPProviderModeLive];
    } else {
        provider = [OPPPaymentProvider paymentProviderWithMode:OPPProviderModeTest];
    }
}

/**
 * tokenize card
 */
RCT_EXPORT_METHOD(tokenizeCard: (NSDictionary*)options resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    NSString *checkoutID = options[@"checkoutID"];
    OPPCheckoutSettings *checkoutSettings = [[OPPCheckoutSettings alloc] init];

    // Set available payment brands for your shop
    checkoutSettings.paymentBrands = @[@"VISA", @"MASTER", @"PAYPAL"];

    // Set shopper result URL
    checkoutSettings.shopperResultURL = @"za.co.shop2shop.payments://result";
    OPPCheckoutProvider *checkoutProvider = [OPPCheckoutProvider checkoutProviderWithPaymentProvider:provider
                                                                                          checkoutID:checkoutID
                                                                                            settings:checkoutSettings];
    [checkoutProvider presentCheckoutForSubmittingTransactionCompletionHandler:^(OPPTransaction * _Nullable transaction, NSError * _Nullable error) {
        if (error) {
            reject(@"oppwa/checkout error", error.localizedDescription, error);
            // Executed in case of failure of the transaction for any reason
        } else if (transaction.type == OPPTransactionTypeSynchronous)  {
            // Send request to your server to obtain the status of the synchronous transaction
            // You can use transaction.resourcePath or just checkout id to do it
            resolve(transaction.resourcePath);
        } else {
            // The SDK opens transaction.redirectUrl in a browser
            // See 'Asynchronous Payments' guide for more details
        }
    } cancelHandler:^{
        reject(@"oppwa/cancelled", @"cancelled", nil);
    }];
}


/**
 * transaction
 */
RCT_EXPORT_METHOD(transactionPayment: (NSDictionary*)options resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {

    NSError * _Nullable error;
   
    
    OPPCardPaymentParams *params = [OPPCardPaymentParams cardPaymentParamsWithCheckoutID:[options valueForKey:@"checkoutID"]

                                                                        paymentBrand:[options valueForKey:@"paymentBrand"]
                                                                              holder:[options valueForKey:@"holderName"]
                                                                              number:[options valueForKey:@"cardNumber"]
                                                                         expiryMonth:[options valueForKey:@"expiryMonth"]
                                                                          expiryYear:[options valueForKey:@"expiryYear"]
                                                                                 CVV:[options valueForKey:@"cvv"]
                                                                               error:&error];

    if (error) {
      reject(@"oppwa/card-init",error.localizedDescription, error);
    } else {
      params.tokenizationEnabled = YES;
      OPPTransaction *transaction = [OPPTransaction transactionWithPaymentParams:params];

      [provider submitTransaction:transaction completionHandler:^(OPPTransaction * _Nonnull transaction, NSError * _Nullable error) {
        if (transaction.type == OPPTransactionTypeAsynchronous) {
          // Open transaction.redirectURL in Safari browser to complete the transaction
        }  else if (transaction.type == OPPTransactionTypeSynchronous) {
         resolve(transaction);
        } else {
          reject(@"oppwa/transaction",error.localizedDescription, error);
          // Handle the error
        }
      }];
    }
}

+ (BOOL)requiresMainQueueSetup
{
    return YES;
}

@end

/**
 * @providesModule Oppwa
 * @flow
 */
import {
    NativeEventEmitter,
    NativeModules,
} from "react-native"
import INTERNALS from "./internals"
import { isObject, } from "./utils"

const OppwaCoreModule = NativeModules.RNOppwa

class Oppwa {
    mode = "test"
    
    constructor() {
        if (!OppwaCoreModule) {
            throw new Error(INTERNALS.ERROR_MISSING_CORE)
        }
        
    }
    
    /**
     * SDK transactionPayment
     *
     * @return Promise
     * @param mode
     */
    setup(mode: String) {
        this.mode = mode.toLowerCase()
        return OppwaCoreModule.setup({mode: mode})
    }
    
    /**
     * tokenize a card using the built in ui
     *
     * @param options
     * @return Promise
     */
    async tokenizeCard(options: Object = {}) {
        if (!isObject(options)) {
            throw new Error(INTERNALS.ERROR_INIT_OBJECT)
        }
        
        if (!options.checkoutID) {
            throw new Error(INTERNALS.ERROR_MISSING_OPT("checkoutID"))
        }
        const result = await OppwaCoreModule.tokenizeCard(options)
        let server = "https://test.oppwa.com"
        if (this.mode === "live") {
            server = "https://oppwa.com"
        }
        server = server + result
        return fetch(server, {method: "GET"})
        .then(resp => {
            console.log("response - " + JSON.stringify(resp))
            return resp.json()
        })
        .then(body => {
            console.log("body - " + JSON.stringify(body))
            const {id, paymentBrand, card} = body
            const name = paymentBrand + " - " + card.last4Digits
            return {id: id, name: name}
            
        })
        .catch((e) => {
            console.log("error - " + e)
            throw e
        })
        
    }
    
    /**
     * SDK transactionPayment
     *
     * @param options
     * @return Promise
     */
    transactionPayment(options: Object = {}) {
        if (!isObject(options)) {
            throw new Error(INTERNALS.ERROR_INIT_OBJECT)
        }
        
        if (!options.checkoutID) {
            throw new Error(INTERNALS.ERROR_MISSING_OPT("checkoutID"))
        }
        if (!options.holderName) {
            throw new Error(INTERNALS.ERROR_MISSING_OPT("holderName"))
        }
        
        if (!options.cardNumber) {
            throw new Error(INTERNALS.ERROR_MISSING_OPT("cardNumber"))
        }
        
        if (!options.paymentBrand) {
            throw new Error(INTERNALS.ERROR_MISSING_OPT("paymentBrand"))
        }
        
        if (!options.expiryMonth) {
            throw new Error(INTERNALS.ERROR_MISSING_OPT("expiryMonth"))
        }
        
        if (!options.expiryYear) {
            throw new Error(INTERNALS.ERROR_MISSING_OPT("expiryYear"))
        }
        
        if (!options.cvv) {
            throw new Error(INTERNALS.ERROR_MISSING_OPT("cvv"))
        }
        
        return OppwaCoreModule.transactionPayment(options)
    }
    
    isValidNumber(options: Object = {}) {
        if (!isObject(options)) {
            throw new Error(INTERNALS.ERROR_INIT_OBJECT)
        }
        
        if (!options.cardNumber) {
            throw new Error(INTERNALS.ERROR_MISSING_OPT("cardNumber"))
        }
        
        return OppwaCoreModule.isValidNumber(options)
    }
}

export default new Oppwa()

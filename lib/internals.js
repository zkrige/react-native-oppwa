import {
    NativeModules,
    Platform
} from "react-native"

export default {
    
    ERROR_INIT_OBJECT:
        "Error initializing - invalid options object",
    /**
     * @param optName
     * @return {string}
     * @constructor
     */
    ERROR_MISSING_OPT(optName) {
        return `Failed to initialize app. Invalid '${optName}' property.`
    },
    
}

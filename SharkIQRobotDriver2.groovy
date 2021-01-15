/**
 *  Shark IQ Robot 2
 *
 *  Copyright 2021 Chris Stevens
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *  
 *  GitHub link: https://github.com/TheChrisTech/Hubitat-SharkIQRobot
 *
 *  Readme is outlined in README.md
 *  Change History is outlined in CHANGELOG.md
 *
 */

import groovy.json.*
import java.util.regex.*
import java.text.SimpleDateFormat

metadata {
    definition (name: "Shark IQ Robot 2", namespace: "cstevens", author: "Chris Stevens") {    
        capability "Actuator"
        capability "Switch"
        capability "Refresh"
        command "start"
        command "returnToDock"
        command "pause"
        command "locate"
        command "setPowerMode", [[name:"Set Power Mode", type: "ENUM",description: "Set Power Mode", constraints: ["Eco", "Normal", "Max"]]]

        attribute "Battery_Level", "integer"
        attribute "Operating_Mode", "text"
        attribute "Power_Mode", "text"
        attribute "Charging_Status", "text"
        //attribute "RSSI", "text"
        attribute "Error_Code","text"
       // attribute "Robot_Volume","text"
        //attribute "Firmware_Version","text"
        attribute "Last_Refreshed","text"
        //attribute "Recharging_To_Resume","text"
         attribute "Locate","text"
    }
 
    preferences {
        input(name: "loginusername", type: "string", title: "Email", description: "Shark Account Email Address", required: true, displayDuringSetup: true)
        input(name: "loginpassword", type: "password", title: "Password", description: "Shark Account Password", required: true, displayDuringSetup: true)
        input(name: "sharkdevicename", type: "string", title: "Device Name", description: "Name you've given your Shark Device within the App", required: true, displayDuringSetup: true)
        input(name: "mobiletype", type: "enum", title: "Mobile Device", description: "Type of Mobile Device your Shark is setup on", required: true, displayDuringSetup: true, options:["Apple iOS", "Android OS"])
        input(name: "refreshInterval", type: "integer", title: "Refresh Interval", description: "Number of seconds between Smart State Refreshes", required: true, displayDuringSetup: true, defaultValue: 60)
        input(name: "smartRefresh", type: "bool", title: "Smart State Refresh", description: "If enabled, Vacuum will refresh (per interval) while running, then every 5 minutes until Fully Charged", required: true, displayDuringSetup: true, defaultValue: true)
         input(name: "refreshEnable", type: "bool", title: "Scheduled Refresh", description: "If enabled, Vacuum will refresh every 30 minutes  while fully charged", defaultValue: false)
        input(name: "debugEnable", type: "bool", title: "Enable Debug Logging", defaultValue: true)
         input(name: "infoLogEnable", type: "bool", title: "Enable Info Logging", defaultValue: true)
    }
}

def updated(){
    if (infoLogEnable)  log.info "Update Triggered"
    unschedule()
     if (refreshEnable) 
    runEvery30Minutes(refreshSch)
    if (infoLogEnable)  log.info "30 min Refresh Schedlue Started"  
}
    
def refreshSch(){
    if (infoLogEnable)  log.info " Refresh 30 min Schedule  triggered"  
    if (device.currentValue('Charging_Status') == "Not Charging") {
        refresh()
        if (infoLogEnable)  log.info "Refresh schedule found Vacuum running changing to smart refresh"  
    }
    else if  (device.currentValue('Charging_Status') == "Charging") {
        refresh()
        if (infoLogEnable)  log.info "Refresh schedule found Vacuum running changing to smart refresh -charging"
    }
         else if (device.currentValue('Charging_Status') == "Fully Charged") {
     grabSharkInfo()
      if (infoLogEnable)  log.info " Refresh Schedule  30 min  Complete" 
    }
}
    

def refresh() {
    logging("d", "Refresh Triggered.")
     if (infoLogEnable)  log.info "Refresh Triggered"  
    grabSharkInfo()
    if (smartRefresh) 
    {
        if (operatingMode in ["Paused", "Running", "Returning to Dock", "Recharging to Continue"])
        {
            logging("d", "Refresh scheduled in $refreshInterval seconds.")
             if (infoLogEnable)  log.info "Refresh scheduled in $refreshInterval seconds"
            runIn("$refreshInterval".toInteger(), refresh)
        }
        else if (operatingMode in ["Docked"] && batteryCapacity.toString() != "100")
        {
            logging("d", "Refresh scheduled in 300 seconds.")
            if (infoLogEnable) log.info ("d", "Refresh scheduled in 300 seconds.")
            runIn(300, refresh)
        }
    }
}

def locate() {
    if (infoLogEnable)  log.info "Locate pushed"    
     runPostDatapointsCmd("SET_Find_Device", 1)
    sendEvent(name:"Locate",value:"active")
    runIn(3,locateOff)
    runIn(10, refresh)
}

def locateOff() {
    sendEvent(name:"Locate",value:"not active")
}

def start(){
    on()
}

def returnToDock(){
    off()
}

 
def on() {
    if (infoLogEnable)  log.info "Vacuum Starting"    
    runPostDatapointsCmd("SET_Operating_Mode", 2)
    sendEvent(name:"Operating_Mode",value:"Running")
    runIn(12, refresh)
}
 
def off() {
    if (infoLogEnable)  log.info "Vacuum Returning to Dock"    
    def stopresults = runPostDatapointsCmd("SET_Operating_Mode", 3)
    sendEvent(name:"Operating_Mode",value:"Returning to Dock")
    logging("d", "$stopresults")
    runIn(12, refresh)
}

def pause() {
    if (infoLogEnable)  log.info "Vacuum Paused"    
    runPostDatapointsCmd("SET_Operating_Mode", 0)
    sendEvent(name:"Operating_Mode",value:"Paused")
    runIn(12, refresh)
}

def setPowerMode(String powermode) {
    power_modes = ["Normal", "Eco", "Max"]
    powermodeint = power_modes.indexOf(powermode)
    if (powermodeint >= 0) { runPostDatapointsCmd("SET_Power_Mode", powermodeint) }
    if (infoLogEnable) log.info "Power Mode Changed"
    runIn(10, refresh)
}

def grabSharkInfo() {
    propertiesResults = runGetPropertiesCmd("names[]=GET_Battery_Capacity&names[]=GET_Recharging_To_Resume&names[]=GET_Charging_Status&names[]=GET_Operating_Mode&names[]=GET_Power_Mode&names[]=GET_Error_Code&names")
    propertiesResults.each { singleProperty ->
        if (singleProperty.property.name == "GET_Battery_Capacity")
        {
            sendEvent(name: "Battery_Level", value: "$singleProperty.property.value", display: true, displayed: true)
            batteryCapacity = singleProperty.property.value
        }
        else if (singleProperty.property.name == "GET_Charging_Status")
        {
            chargingStatusValue = singleProperty.property.value
        }
        else if (singleProperty.property.name == "GET_Operating_Mode")
        {
            operatingModeValue = singleProperty.property.value
        }
        else if (singleProperty.property.name == "GET_Power_Mode")
        {
            power_modes = ["Normal", "Eco", "Max"]
            sendEvent(name: "Power_Mode", value: power_modes[singleProperty.property.value], display: true, displayed: true)
        }
        else if (singleProperty.property.name == "GET_Error_Code")
        {
            error_codes = ["No error", "Side wheel is stuck","Side brush is stuck","Suction motor failed","Brushroll stuck","Side wheel is stuck (2)","Bumper is stuck","Cliff sensor is blocked","Battery power is low","No Dustbin","Fall sensor is blocked","Front wheel is stuck","Switched off","Magnetic strip error","Top bumper is stuck","Wheel encoder error"]
            sendEvent(name: "Error_Code", value: error_codes[singleProperty.property.value], display: true, displayed: true)
        }
    }

    // Charging Status
    // chargingStatusValue - 0 = NOT CHARGING, 1 = CHARGING
    charging_status = ["Not Charging", "Charging"]
    if (device.currentValue('Battery_Level') == "100"&& operatingModeValue.toString() == "3") { 
        chargingStatusToSend = "Fully Charged" 
        if (infoLogEnable)  log.info "Vacuum Fully Charged"    
    }
    else {
        chargingStatusToSend = charging_status[chargingStatusValue]
    }
    sendEvent(name: "Charging_Status", value: chargingStatusToSend, display: true, displayed: true) 
    operating_modes = ["Paused", "Stopped", "Running", "Returning to Dock"]
       if  (operatingModeValue.toString() == "3") {
        if (device.currentValue('Charging_Status') == "Fully Charged") {
            operatingModeToSend = "Docked" 
            sendEvent(name:"switch",value:"off")
            if (infoLogEnable)  log.info "Vacuum Docked"    
        }
        else if (device.currentValue('Charging_Status') == "Charging"){
            operatingModeToSend = "Docked" 
            sendEvent(name:"switch",value:"off")
             if (infoLogEnable)  log.info "Vacuum Docked" 
        }
        else {
            operatingModeToSend = "Returning to Dock" 
             if (infoLogEnable)  log.info "Returning to Dock" 
        }
    }
    else {
        operatingModeToSend = operating_modes[operatingModeValue] 
        if (operatingModeValue.toString() == "2") { 
         sendEvent(name:"switch",value:"on")
         if (infoLogEnable)  log.info "Vacuum Running"            
        }
    }
    
    sendEvent(name: "Operating_Mode", value: operatingModeToSend, display: true, displayed: true)
    operatingMode = operatingModeToSend

    def date = new Date()
    sendEvent(name: "Last_Refreshed", value: "$date", display: true, displayed: true)
}

def initialLogin() {
    login()
    getDevices()
    getUserProfile()
}

def runPostDatapointsCmd(String operation, Integer operationValue) {
    initialLogin()
    def localDevicePort = (devicePort==null) ? "80" : devicePort
	def params = [
        uri: "https://ads-field.aylanetworks.com",
		path: "/apiv1/dsns/$dsnForDevice/properties/$operation/datapoints.json",
        requestContentType: "application/json",
        headers: ["Content-Type": "application/json", "Accept": "*/*", "Authorization": "auth_token $authtoken"],
        body: "{\"datapoint\":{\"value\":\"$operationValue\",\"metadata\":{\"userUUID\":\"$uuid\"}}}"
    ]
    performHttpPost(params)
}

def runGetPropertiesCmd(String operation) {
    initialLogin()
    def localDevicePort = (devicePort==null) ? "80" : devicePort
	def params = [
        uri: "https://ads-field.aylanetworks.com",
		path: "/apiv1/dsns/$dsnForDevice/properties.json",
        requestContentType: "application/json",
        headers: ["Content-Type": "application/json", "Accept": "*/*", "Authorization": "auth_token $authtoken"],
        queryString: "$operation".toString()
    ]
    performHttpGet(params)
}

private performHttpPost(params) {
    try {
        httpPost(params) { response ->
            if(response.getStatus() == 200 || response.getStatus() == 201) {
                results = response.data
                logging("d", "Response received from Shark in the postResponseHandler. $response.data")
            }
            else {
                logging("e", "Shark failed. Shark returned ${response.getStatus()}.")
                logging("e", "Error = ${response.getErrorData()}")
            }
        }
    } 
    catch (e) {
        logging("e", "Error during performHttpPost: $e")
    }
    return results
}

private performHttpGet(params) {
    try {
        httpGet(params) { response ->
            if(response.getStatus() == 200 || response.getStatus() == 201) {
                results = response.data
                logging("d", "Response received from Shark in the getResponseHandler. $response.data")
            }
            else {
                logging("e", "Shark failed. Shark returned ${response.getStatus()}.")
                logging("e", "Error = ${response.getErrorData()}")
            }
        }
    } 
    catch (e) {
        logging("e", "Error during performHttpGet: $e")
    }
    return results
}

def login() {
    def localDevicePort = (devicePort==null) ? "80" : devicePort
    def app_id = ""
    def app_secret = ""
    if (mobiletype == "Apple iOS") {
        app_id = "Shark-iOS-field-id"
        app_secret = "Shark-iOS-field-_wW7SiwgrHN8dpU_ugCattOoDk8"
    }
    else if (mobiletype == "Android OS") {
        app_id = "Shark-Android-field-id"
        app_secret = "Shark-Android-field-Wv43MbdXRM297HUHotqe6lU1n-w"
    }
	def body = """{"user":{"email":"$loginusername","application":{"app_id":"$app_id","app_secret":"$app_secret"},"password":"$loginpassword"}}"""
    
    //log.info body
	def params = [
        uri: "https://ads-field.aylanetworks.com",
		path: "/users/sign_in.json",
        requestContentType: "application/json",
        headers: ["Content-Type": "application/json", "Accept": "*/*"],
        body: "$body"
    ]
    try {
        httpPost(params) { response ->
            if(response.getStatus() == 200 || response.getStatus() == 201) {
                logging("d","Response received from Shark in the postResponseHandler. $response.data")
                def accesstokenstring = ("$response.data" =~ /access_token:([A-Za-z0-9]*.*?)/)
                authtoken = accesstokenstring[0][1]
                return response
            }
            else {
                logging("e","Shark failed. Shark returned ${response.getStatus()}.")
                logging("e","Error = ${response.getErrorData()}")
            }
        }
    } catch (e) {
    	logging("e", "Error during login: $e")
	}
}

def getUserProfile() {
	def params = [
        uri: "https://ads-field.aylanetworks.com",
		path: "/users/get_user_profile.json",
        headers: ["Content-Type": "application/json", "Accept": "*/*", "Authorization": "auth_token $authtoken"],
    ]
    try {
        httpGet(params) { response ->
            if(response.getStatus() == 200 || response.getStatus() == 201) {
                logging("d","Response received from Shark in the postResponseHandler. $response.data")
                def uuidstring = ("$response.data" =~ /uuid:([A-Za-z0-9-]*.*?)/)
                uuid = uuidstring[0][1]
                return response
            }
            else {
                logging("e", "Shark failed. Shark returned ${response.getStatus()}.")
                logging("e", "Error = ${response.getErrorData()}")
            }
        }
    } catch (e) {
    	logging("e", "Error during getUserProfile: $e")
	}

}

def getDevices() {
	def params = [
        uri: "https://ads-field.aylanetworks.com",
		path: "/apiv1/devices.json",
        headers: ["Content-Type": "application/json", "Accept": "*/*", "Authorization": "auth_token $authtoken"],
    ]
    try {
        httpGet(params) { response ->
            if(response.getStatus() == 200 || response.getStatus() == 201) {
                logging("d", "Response received from Shark in the postResponseHandler. $response.data")
                def devicedsn = ""
                for (devices in response.data.device ) {
                    if ("$sharkdevicename" == "${devices.product_name}")
                    {   
                        dsnForDevice = "${devices.dsn}"
                    }
                }
                if ("$dsnForDevice" == '')
                {
                    logging("e", "$sharkdevicename did not match any product_name on your account. Please verify your `Device Name`.")
                }
                return response
            }
            else {
                logging("e", "Shark failed. Shark returned ${response.getStatus()}.")
                logging("e", "Error = ${response.getErrorData()}")
            }
        }
    } catch (e) {
    	logging("e", "Error during getDevices: $e")
	}

}

/********************************************
*** HELPER METHODS
********************************************/

def logging(String status, String description) {
    if (debugEnable && status == "d"){ log.debug(description) }
    else if (status == "w"){ log.warn(description) }
    else if (status == "e"){ log.error(description) }
}

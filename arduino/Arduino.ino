//Author : Quentin Lombat

#include <ArduinoJson.h>
#include <WiFi101.h>
#include <SPI.h>
#include <Adafruit_Sensor.h>
#include <DHT.h>
#include <DHT_U.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>

//Change following information
char ssid[] = "JuMad";          // your network SSID (name)
char pass[] = "TP-LINK1994";    // your network password (use for WPA, or use as key for WEP)
int keyIndex = 0;               // your network key Index number (needed only for WEP)


//Humidity and temperature configuration
#define DHTPIN            A0        // Pin which is connected to the DHT sensor.
#define DHTTYPE           DHT11     // DHT 11 
float humidity                = 0;    //Don't change
float temperature             = 0;    //Don't change
bool humidityValueChanged     = true; //Don't change
bool temperatureValueChanged  = true; //Don't change
int temperatureTrigger        = 1;    // celcius
int humidityTrigger           = 5;    // %
DHT_Unified dht(DHTPIN, DHTTYPE);     //Don't change

//LCD screen config 
LiquidCrystal_I2C lcd(0x3F, 16, 2);

//Zones configuration
char* zones[7] = {"NO", "NE", "N", "SO", "SE", "S", "BUS"}; //Zone managed by this arduino

//Pin to open foreach zone
int pinLed[7][3] = {
  {1, -1, -1},    //NO
  {2, -1, -1},    //NE
  {1, 2, 3},      //N
  {5, -1, -1},    //SO
  {6, -1, -1},    //SE
  {4, 5, 6},      //S
  {7, -1, -1},    //BUS
};

//API configuration
const char* server = "jsonplaceholder.typicode.com";  // server's address
const char* pathGetZones = "/users/1";
const char* pathPostTemperature = "/users/1";
const char* pathPostHumidity = "/users/1";
const unsigned long HTTP_TIMEOUT = 10000;  // max respone time from server
const size_t MAX_CONTENT_SIZE = 256;       // max size of the HTTP response

//General configuration
int status = WL_IDLE_STATUS;
WiFiClient client;
const unsigned long BAUD_RATE = 9600;

void setup() {
  initSerial();
  //initWifi();
  //initLed();
  lcd.init();
}

void loop() {
  updateTemperatureAndHumidity();
  if (connect(server)) {
    if (getRequest(server, pathGetZones) && skipResponseHeaders()) {
      readReponseContentForZone();
    }
  }
  disconnect();
  delay(3000);
}

// Initialize Serial port
void initSerial() {
  Serial.begin(BAUD_RATE);
  while (!Serial) {
    ;  // wait for serial port to initialize
  }
  Serial.println("Serial ready");
}

// Initialize Led
void initLed() {
  Serial.println("init led");

  //Open all leds
  Serial.print("open leds : ");
  for (int i = 0; i < (int) (sizeof(zones) / sizeof(char*)); i++) {
    for (int j = 0; j < (int) (sizeof(pinLed[i]) / sizeof(int)); j++) {
      if (pinLed[i][j] >= 0) {
        Serial.print(pinLed[i][j]);
        Serial.print(" ");
        pinMode(pinLed[i][j], OUTPUT);
        digitalWrite(pinLed[i][j], HIGH);
      }
    }
  }
  Serial.println();
  delay(5000);

  //Close all leds
  Serial.print("close leds : ");
  for (int i = 0; i < (int) (sizeof(zones) / sizeof(char*)); i++) {
    for (int j = 0; j < (int) (sizeof(pinLed[i]) / sizeof(int)); j++) {
      if (pinLed[i][j] >= 0) {
        Serial.print(pinLed[i][j]);
        Serial.print(" ");
        digitalWrite(pinLed[i][j], LOW);
      }
    }
  }
  Serial.println();
}

void initWifi() {
  // check for the presence of the shield:
  if (WiFi.status() == WL_NO_SHIELD) {
    Serial.println("WiFi shield not present");
    // don't continue:
    while (true);
  }

  // attempt to connect to WiFi network:
  while ( status != WL_CONNECTED) {
    Serial.print("Attempting to connect to SSID: ");
    Serial.println(ssid);
    // Connect to WPA/WPA2 network. Change this line if using open or WEP network:
    status = WiFi.begin(ssid, pass);

    // wait 10 seconds for connection:
    delay(10000);
  }
  // you're connected now, so print out the status:
  printWiFiStatus();
}

void printWiFiStatus() {
  // print the SSID of the network you're attached to:
  Serial.print("SSID: ");
  Serial.println(WiFi.SSID());

  // print your WiFi shield's IP address:
  IPAddress ip = WiFi.localIP();
  Serial.print("IP Address: ");
  Serial.println(ip);

  // print the received signal strength:
  long rssi = WiFi.RSSI();
  Serial.print("signal strength (RSSI):");
  Serial.print(rssi);
  Serial.println(" dBm");
}

// Open connection to the HTTP server
bool connect(const char* hostName) {
  Serial.print("Connect to ");
  Serial.println(hostName);

  bool ok = client.connect(hostName, 80);

  Serial.println(ok ? "Connected" : "Connection Failed!");
  return ok;
}

// Send the HTTP GET request to the server
bool getRequest(const char* host, const char* resource) {
  Serial.print("GET ");
  Serial.println(resource);

  client.print("GET ");
  client.print(resource);
  client.println(" HTTP/1.1");
  client.print("Host: ");
  client.println(host);
  client.println("Connection: close");
  client.println();

  return true;
}

bool postRequest(const char* host, const char* resource, const char* content) {
  Serial.print("POST ");
  Serial.println(resource);
  
  client.print("GET ");
  client.print(resource);
  client.println(" HTTP/1.1");
  client.print("Host: ");
  client.println(host);
  client.println("Accept: */*");
  client.println("Content-Length: " + (int) (sizeof(content) / sizeof(char)));
  client.println("Content-Type: application/x-www-form-urlencoded");
  client.println();
  client.println(content);

  return true;
}

// Skip HTTP headers so that we are at the beginning of the response's body
bool skipResponseHeaders() {
  // HTTP headers end with an empty line
  char endOfHeaders[] = "\r\n\r\n";

  bool ok = client.find(endOfHeaders);

  if (!ok) {
    Serial.println("No response or invalid response!");
  }

  return ok;
}


bool readReponseContentForZone() {
  Serial.println("readReponseContentForZone");
  delay(1000);
  DynamicJsonBuffer jsonBuffer(MAX_CONTENT_SIZE);

  //JsonObject& root = jsonBuffer.parseObject(client);
  JsonObject& root = jsonBuffer.parseObject("{\"zones\" : [ \"S\", \"BUS\", \"NO\"]}");

  if (!root.success()) {
    Serial.println("JSON parsing failed!");
    return false;
  }

  Serial.println("Open all zones");
  //open all zones
  for (int i = 0; i < (int) (sizeof(zones) / sizeof(char*)); i++) {
    for (int j = 0; j < (int) (sizeof(pinLed[i]) / sizeof(int)); j++) {
      if (pinLed[i][j] >= 0) {
        digitalWrite(pinLed[i][j], LOW);
      }
    }
  }

  //Close zone in the response of request
  Serial.println("Close zone : ");
  JsonArray& array = root["zones"];
  //loop on zones managed by this arduino
  for (int i = 0; i < (int) (sizeof(zones) / sizeof(char*)); i++) {
    //loop on zones given by the request
    for (JsonArray::iterator it = array.begin(); it != array.end(); ++it) {
      const char* zone = *it;
      //If zone must be closed
      if (strcmp(zone, zones[i]) == 0) {
        Serial.print(zones[i]);
        //loop on each pin of the current zone
        for (int k = 0; k < (int) (sizeof(pinLed[i]) / sizeof(int)); k++) {
          if (pinLed[i][k] >= 0) {
            digitalWrite(pinLed[i][k], HIGH);
          }
        }
        Serial.println();
      }
    }
  }

  return true;
}

// Close the connection with the HTTP server
void disconnect() {
  Serial.println("Disconnect");
  client.stop();
}

void updateTemperatureAndHumidity() {
  dht.begin();
  sensors_event_t event;
  dht.temperature().getEvent(&event);
  if (isnan(event.temperature)) {
    Serial.println("Error reading temperature!");
  }
  else {
    Serial.print("Temperature: ");
    Serial.print(event.temperature);
    Serial.println(" *C");

    if (abs(temperature - event.temperature) >= temperatureTrigger) {
      temperature = event.temperature;
      temperatureValueChanged = true;
    }
  }
  // Get humidity event and print its value.
  dht.humidity().getEvent(&event);
  if (isnan(event.relative_humidity)) {
    Serial.println("Error reading humidity!");
  }
  else {
    Serial.print("Humidity: ");
    Serial.print(event.relative_humidity);
    Serial.println("%");

    if (abs(humidity - event.relative_humidity) >= humidityTrigger) {
      humidity = event.relative_humidity;
      humidityValueChanged = true;
    }
  }

  //Show temperature and humidity on lcd screen
  lcd.backlight();
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("Temperature:");
  lcd.setCursor(13, 0);
  lcd.print((int)temperature);
  lcd.setCursor(15, 0);
  lcd.print("C");
  lcd.setCursor(0, 1);
  lcd.print("Humidity:");
  lcd.setCursor(13, 1);
  lcd.print((int)humidity);
  lcd.setCursor(15, 1);
  lcd.print("%");
}

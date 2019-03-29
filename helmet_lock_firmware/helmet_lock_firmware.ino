
/*********************************************************************
 Author: Timothy Barnes
 Email: timothy@veratekdesign.com
 Date: 2019-03-26

 Updated By : Allan Murillo
 Date : 3/29/2019
*********************************************************************/

#include <Arduino.h>
#include <SPI.h>
#include "Adafruit_BLE.h"
#include "Adafruit_BluefruitLE_SPI.h"
#include "Adafruit_BluefruitLE_UART.h"

#include "BluefruitConfig.h"

#define CLOCKWISE_LOCK true
const int MOTOR_TURNING_TIME = 1000;

const int MS_BEFORE_CLOSING_LOCK_IF_OPEN = 10000;

const int LED_PIN = 13;

const int AIN1 = A0;
const int AIN2 = A1;
const int SLP = A2;

#define VBATPIN A9

enum program_state
{
  READING_GATT_CHARACTERISTIC,
  CLOSING_LOCK,
  OPENING_LOCK,
  WRITING_GATT_CHARACTERISTIC
};

char* batteryLevel;

static program_state programState = READING_GATT_CHARACTERISTIC;

static bool helmetIsUnlocked = true;
static unsigned long unlockedStartingTime = 0;

Adafruit_BluefruitLE_SPI ble(BLUEFRUIT_SPI_CS, BLUEFRUIT_SPI_IRQ, BLUEFRUIT_SPI_RST);

const int MAX_LINES = 5;
const int MAX_CHAR_PER_LINE = 128;
static char replyLines[MAX_LINES][MAX_CHAR_PER_LINE];


void BleModuleCommandBlocking(Adafruit_BluefruitLE_SPI *ble, char *commandStr, char *replyLineBuffer)
{
  // Send AT command string to BLE module
  ble->println(commandStr);

  // Read reply from BLE module
  
  int line = 0;
  
  memset(replyLineBuffer, sizeof(char)*MAX_LINES*MAX_CHAR_PER_LINE, 0);

  for(;;)
  {
    char *currentLine = &replyLineBuffer[line*MAX_CHAR_PER_LINE];
    
    if(!ble->readline(currentLine, MAX_CHAR_PER_LINE))
    {
      break;
    }

    if(strncmp(currentLine, "OK", 2) == 0)
    {
      break;
    }
    else
    {
      if(line < MAX_LINES-1)
      {
        line += 1;
      }
      else
      {
        break;
      }
    }

    delay(1);
  }
}

void CloseLock()
{
  helmetIsUnlocked = false;
  #if CLOCKWISE_LOCK
  digitalWrite(AIN1, LOW);
  digitalWrite(AIN2, HIGH);
  #else
  digitalWrite(AIN1, HIGH);
  digitalWrite(AIN2, LOW);
  #endif
  
  delay(MOTOR_TURNING_TIME);

  digitalWrite(AIN1, LOW);
  digitalWrite(AIN2, LOW);

  digitalWrite(LED_PIN, LOW);
}

void OpenLock()
{
  helmetIsUnlocked = true;
  digitalWrite(LED_PIN, HIGH);
      
  #if CLOCKWISE_LOCK
  digitalWrite(AIN1, HIGH);
  digitalWrite(AIN2, LOW);
  #else
  digitalWrite(AIN1, LOW);
  digitalWrite(AIN2, HIGH);
  #endif
  
  delay(MOTOR_TURNING_TIME);

  digitalWrite(AIN1, LOW);
  digitalWrite(AIN2, LOW);
}

void setup(void)
{
  pinMode(LED_PIN, OUTPUT);
  pinMode(AIN1, OUTPUT);
  pinMode(AIN2, OUTPUT);
  pinMode(SLP, OUTPUT);

  digitalWrite(SLP, HIGH); // Logic high enables the device (DRV8833 Dual H-Bridge Motor Driver datasheet (Rev. E) Page 3)

  CloseLock(); // Set lock to close position so that the starting GATT characteristic matches the orientation of the lock.
  
  delay(500);

  Serial.begin(115200);
  ble.begin(VERBOSE_MODE);
  ble.echo(false);

  Serial.println("======= Helmet Lock Firmware =========");

  BleModuleCommandBlocking(&ble, "AT+GATTCLEAR", replyLines[0]);

  BleModuleCommandBlocking(&ble, "AT+GAPDEVNAME", replyLines[0]);

  if(strncmp(replyLines[0], "HelmetLock", 9) != 0)
  {
    BleModuleCommandBlocking(&ble, "AT+GAPDEVNAME=HelmetLock", replyLines[0]);
  }
  
  BleModuleCommandBlocking(&ble, "AT+GATTADDSERVICE=UUID=0x180F", replyLines[0]);
  BleModuleCommandBlocking(&ble, "AT+GATTADDCHAR=UUID=0x2A19,PROPERTIES=0x0A,MIN_LEN=1,VALUE=100", replyLines[0]); // Property 0x0A means read/write (0x02 read + 0x08 write)
  
  Serial.println("Setup complete");

  // Blink the LED a few times to signify startup.
  for(int i = 0; i < 10; i++)
  {
    digitalWrite(LED_PIN, HIGH);
    delay(100);
    digitalWrite(LED_PIN, LOW);
    delay(100);
  }
}

void loop(void)
{
  unsigned long currentTime = millis();
  
  switch(programState)
  {
    case READING_GATT_CHARACTERISTIC:
    {
      bool closeLockIfOpenedTooLong = false;
      
      if(helmetIsUnlocked)
      {
        if((currentTime - unlockedStartingTime) > MS_BEFORE_CLOSING_LOCK_IF_OPEN)
        {
          closeLockIfOpenedTooLong = true;      
        }
      }

      if(closeLockIfOpenedTooLong)
      {
        programState = CLOSING_LOCK;
        closeLockIfOpenedTooLong = false;
      }
      else
      {
        // Monitor the GATT characteristic to respond if it changes        
        ble.println("AT+BLEUARTRX");
        ble.readline();

        if (strcmp(ble.buffer, "OK") == 0) {
          // no data
        }
        else
        {
          if(strncmp(ble.buffer, "0", 1) == 0)
          {
            if(helmetIsUnlocked)
            {
              programState = CLOSING_LOCK;
            }
          }
          else if(strncmp(ble.buffer, "1", 1) == 0)
          {
            if(!helmetIsUnlocked)
            {
              programState = OPENING_LOCK;
            }
          }
          else if(strncmp(ble.buffer, "s", 1) == 0)
          {
//            if(helmetIsUnlocked)
//              ble.println("AT+BLEUARTTX=1");
//            else
//              ble.println("AT+BLEUARTTX=0");
          }
          else{
            CloseLock();
          }
        }
      }
    }break; 
    case CLOSING_LOCK:
    {
      CloseLock();      
      programState = READING_GATT_CHARACTERISTIC;
    }break;
    case OPENING_LOCK:
    {
      OpenLock();      
      unlockedStartingTime = currentTime;
      programState = READING_GATT_CHARACTERISTIC;
    }break;
  }

  delay(100);

  float measuredvbat = analogRead(VBATPIN);
  measuredvbat *= 2;    // we divided by 2, so multiply back
  measuredvbat *= 3.3;  // Multiply by 3.3V, our reference voltage
  measuredvbat /= 1024; // convert to voltage
//  Serial.print("VBat: " ); Serial.println(measuredvbat);

  int lvl = 0;
  if(measuredvbat >= 4)
    lvl = 4;
  else if(measuredvbat >= 3 && measuredvbat < 4)
    lvl = 3;
  else if(measuredvbat >= 2 && measuredvbat < 3)
    lvl = 2;
  else
    lvl = 1;

  batteryLevel = "AT+GATTCHAR=1,";
  char integer_string[1];
  
  sprintf(integer_string, "%d", lvl);
  strcat(batteryLevel, integer_string);
  
  BleModuleCommandBlocking(&ble, batteryLevel, replyLines[0]);

  delay(100);
}

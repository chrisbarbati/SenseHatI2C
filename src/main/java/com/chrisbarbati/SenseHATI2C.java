package com.chrisbarbati;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.i2c.I2C;
import com.pi4j.io.i2c.I2CConfig;
import com.pi4j.io.i2c.I2CProvider;

/**
 * Christian Barbati - Dec 2023
 *
 * Class to add methods that make it easier to interact with
 * the Raspberry Pi Sense Hat
 */

public class SenseHATI2C
{
    /**
     * I2C _addresses of the various chips on the Sense HAT
     */
    static final int LPS25H_ADDRESS = 0x5c; //Pressure (temp)
    static final int HTS221_ADDRESS = 0x5f; //Humidity (temp)
    static final int LSM9DS1_ADDRESS = 0x1c; //Accelerometer. Docs also show 0x6a, test later
    static final int LED2472G_ADDRESS = 0x46; //ATTiny88 (LEDs, joystick, colour sensor(?))

    /**
     * I2C registers where data is read from the chips
     */
    static final int LPS25H_TEMP_OUT_H_REGISTER = 0x2c;
    static final int LPS25H_TEMP_OUT_L_REGISTER = 0x2b;

    static final int LPS25H_PRESS_OUT_H_REGISTER = 0x2A;
    static final int LPS25H_PRESS_OUT_L_REGISTER = 0x29;
    static final int LPS25H_PRESS_OUT_XL_REGISTER = 0x28;

    /**
     * Returns a double representing the current temperature reading in degrees Celsius, as read by the LPS25H pressure sensor
     * 
     * @return Current temperature in degrees Celsus, as a double
     */
    public static double getTempFromPressure(){
        double temperature = 0;

        I2C tempI2C = getI2C("TEMPFROMPRESSURE", LPS25H_ADDRESS);

        try  {

            if(!initializeLPS25H(tempI2C)){
                //Add proper exception-handling here later
                System.out.println("Error initializing LPS25H");
            }

            /**
             * Get the high and low portions of the temperature value from the appropriate registers
             * 
             * tempHigh represents the leftmost 8 bits of the value, and tempLow represents the rightmost 8 bits
             */
            byte tempHigh = tempI2C.readRegisterByte(LPS25H_TEMP_OUT_H_REGISTER);
            byte tempLow = tempI2C.readRegisterByte(LPS25H_TEMP_OUT_L_REGISTER);

            /*
            * Concatenate the two bytes and store as short
            */
            short tempFull = (short)((tempHigh << 8) | (tempLow & 0xFF));

            double cycles = (double)tempFull;

            //Temperature offset is cycles/480, relative to a base number of 42.5 degrees Celsius
            temperature = 42.5 + (cycles/480);
        } catch (Exception e){
            System.out.println(e);
        }

        return temperature;
    }

    /**
     * Initializes the appropriate registers on the LPS25H
     * to enable reading temperature / pressure.
     * 
     * @param tempI2C The I2C object to be used to initialize the LPS25H sensor (must have been created with the LPS25H address as it's parameter)
     * @return True if successful
     */
    public static boolean initializeLPS25H(I2C tempI2C){
        try{
            //Set CTRL_REG1. Enable output, set data rate to 25Hz, don't update output registers until MSB and LSB update
            tempI2C.writeRegister(0x20, 0xc4);
            //Set RES_CONF. Set temp internal avereage to 16, pressure internal average to 32
            tempI2C.writeRegister(0x10, 0x05);
            //Set FIFO_CTRL. Set FIFO to generate a running average filtered pressure
            tempI2C.writeRegister(0x2E, 0xc0);
            //Set CTRL_REG4. Unclear what this does in RTIMU, doing it here until I can test.
            tempI2C.writeRegister(0x23, 0x40);

            //Set CTRL_REG2, send one shot signal
            tempI2C.writeRegister(0x21, 0x01);

            //Set FIFO to bypass
            tempI2C.writeRegister(0x2E, 0);

            //Wait for one-shot to reset before proceeding, indicating a sample has been taken
            //Also await the P_DA and T_DA registers of the STATUS_REG being reset to 1, indicating a new reading for pressure and temp is ready
            while(tempI2C.readRegister(0x21) != 0 || ((tempI2C.readRegisterByte(0x27) & 0b11) != 0b11)){

            }

            return true;
        } catch (Exception e){
            System.out.println(e);
            return false;
        }
    }

    /**
     * Initializes the appropriate registers on the HTS221
     * to enable reading temperature / pressure.
     * 
     * @param humidityI2C The I2C object to be used to initialize the LPS25H sensor (must have been created with the LPS25H address as it's parameter)
     * @return True if successful
     */
    public synchronized static boolean initializeHTS221(I2C humidityI2C){
        try{

            //Initialization
            //Set power status to on, BDU to non-continuous mode
            humidityI2C.writeRegister(0x20, 0x84);
            //Send the "one-shot" signal
            humidityI2C.writeRegister(0x21, 0x01);

            //Wait for the one-shot register to set itself back to zero, indicating a reading is ready.
            while(humidityI2C.readRegister(0x21) != 0){

            }

            return true;
        } catch (Exception e){
            System.out.println(e);
            return false;
        }
    }

    /**
     * Returns a double representing the current pressure in millibar, as read by the LPS25H pressure sensor
     * 
     * @return Current atmospheric pressure in millibar, as double
     */
    public static double getPressureMbar(){
        double pressure = 0;

        I2C pressureI2C = getI2C("PRESSURE", LPS25H_ADDRESS);

        if(!initializeLPS25H(pressureI2C)){
            //Add proper exception-handling here later
            System.out.println("Error initializing LPS25H");
        }

        try  {

            int pressureH = pressureI2C.readRegister(LPS25H_PRESS_OUT_H_REGISTER);
            int pressureL = pressureI2C.readRegister(LPS25H_PRESS_OUT_L_REGISTER);
            int pressureXL = pressureI2C.readRegister(LPS25H_PRESS_OUT_XL_REGISTER);

            /**
             * Concatenate the three 8-bit values to create a signed 24-bit value
            */
            int pressureFull = (int)(((pressureH &  0xFF) << 16) | ((pressureL & 0xFF) << 8) | (pressureXL & 0xFF));

            double cycles;

            cycles = (double)(pressureFull);

            pressure = (cycles/4096);

        } catch (Exception e){
            System.out.println(e);
        }

        return pressure;
    }

    /**
     * Returns a double representing the current atmospheric in PSI, as read by the LPS25H pressure sensor
     * 
     * @return Current atmospheric pressure in PSI, as double
     */
    public static double getPressurePSI(){
        return getPressureMbar() / 68.948;
    }

    /**
     * I've noticed that the two temperature readings don't always agree, so I 
     * have added this function to average the two values.
     * 
     * Returns a temperature value averaged from both LPS25H and HTS221 sensors. 
     * @return
     */
    public static double getTempAveraged(){
        return (getTempFromPressure() + getTempFromHumidity()) / 2;
    }

    /**
     * Returns a double representing the current temperature reading in degrees Celsius, as read by the HTS221 humidity sensor
     * 
     * @return Current temperature in degrees Celsius, as double
     */
    public static double getTempFromHumidity(){
        double temp = 0;
        I2C tempI2C = getI2C("TEMPFROMHUMIDITY", HTS221_ADDRESS);

        try  {

            //Initialization
            //Set power status to on, BDU to non-continuous mode
            tempI2C.writeRegister(0x20, 0x84);
            //Send the "one-shot" signal
            tempI2C.writeRegister(0x21, 0x01);

            //Wait for the one-shot register to set itself back to zero, indicating a reading is ready.
            while(tempI2C.readRegister(0x21) != 0){

            }

            /**
             * Temperature readings are found by combining the following:
             * MSB T0_degC T0_degC, 0x35 bit 3, 2 concatenated to 0x32
             *
             * MSB T1_degC T1_degC, 0x35 bit 1, 0 concatenated to 0x33
             *
             * MSB at 0x35 is a an 8-bit value (only 4 rightmost bits significant), so we need to retrieve it and split
             */

            int msb = tempI2C.readRegister(0x35);
            String msbString = Integer.toBinaryString(msb);

            msbString = fillBits(msbString, 8);

            //The MSB has an 8 bit value, but we only need two bits for each calibration value
            String msbT0 = msbString.substring(7, 8);
            String msbT1 = msbString.substring(5, 6);

            int t0Cal = tempI2C.readRegister(0x32);
            int t1Cal = tempI2C.readRegister(0x33);

            String t1CalString = Integer.toBinaryString(t1Cal);
            String t0CalString = Integer.toBinaryString(t0Cal);

            //The calibration values are 10 bits, unsigned. Concatenate the MSBs
            t0CalString = msbT0 + fillBits(t0CalString, 8);
            t1CalString = msbT1 + fillBits(t1CalString, 8);

            /**
             * Convert the values to doubles, and divide by 8 (datasheet indicates that the registers hold a value
             * representing 8x the actual calibration value)
             */
            double t0CalDouble = Integer.parseInt(t0CalString, 2) / 8;
            double t1CalDouble = Integer.parseInt(t1CalString, 2) / 8;

            /**
             * The values we just calculated represent the y-values of two points.
             *
             * To determine the equation of the line that describes the temperature
             * reading, we also need the x-values, represented by the T0 and T1 values
             */

            //T0 and T1 are stored in two separate registers each, as 8-bit values
            byte t0High = tempI2C.readRegisterByte(0x3D);
            byte t0Low = tempI2C.readRegisterByte(0x3C);
            byte t1High = tempI2C.readRegisterByte(0x3F);
            byte t1Low = tempI2C.readRegisterByte(0x3E);

            /*
             * t0 and t1 are a each signed 16-bit values, so we need to concatenate them
             */
            short t0 = (short)((t0High << 8) | (t0Low & 0xFF));
            short t1 = (short)((t1High << 8) | (t1Low & 0xFF));

            double t0Double = (double)t0;
            double t1Double = (double)t1;

            /**
             * Now that we have two points, we can calculate the slope
             * by dividing rise by run
             */
            Double slope = (t1CalDouble - t0CalDouble) / (t1Double - t0Double);

            /**
             * And the y-intercept can be determined by isolating for it
             * in the formula (y = mx + b, b = y - mx)
             */
            Double b = t1CalDouble - (slope * t1Double);

            /**
             * The value in TOUT represents the independent variable for the above line equation.
             * It is represented by a signed 16-bit value.
             */
            byte tOutHigh = tempI2C.readRegisterByte(0x2B);
            byte tOutLow = tempI2C.readRegisterByte(0x2A);

            short tOut = (short)((tOutHigh << 8) | (tOutLow & 0xFF));
            double tOutDouble = (double)tOut;

            /**
             * Now that we have the equation for our line, and the independent
             * variable represented by tOut, we can calculate our temperature
             * reading (degrees Celsius)
             */
            temp = (slope * tOutDouble) + b;
        } catch (Exception e){
            System.out.println(e);
        }


        return temp;
    }

    /**
     * Returns a double representing the current relative humidity (%), as read by the HTS221 humidity sensor
     * 
     * @return Current relative humidity (%), as double
     */
    public static double getHumidity(){
        double humidity = 0;
        I2C humI2C = getI2C("HUMIDITY", HTS221_ADDRESS);

        try  {

            //Initialization
            //Set power status to on, BDU to non-continuous mode
            humI2C.writeRegister(0x20, 0x84);
            //Send the "one-shot" signal
            humI2C.writeRegister(0x21, 0x01);

            //Wait for the one-shot register to set itself back to zero, indicating a reading is ready.
            while(humI2C.readRegister(0x21) != 0){

            }

            //The two calibration values are stored as 2x value
            double h0Cal = (double)humI2C.readRegister(0x30) / 2;
            double h1Cal = (double)humI2C.readRegister(0x31) / 2;

            /**
             * The values we just calculated represent the y-values of two points.
             *
             * To determine the equation of the line that describes the humidity
             * reading, we also need the x-values, represented by the H0 and H1 values
             */

            //H0 and H1 are stored in two separate registers each, as a signed 16 bit value.
            byte h0High = humI2C.readRegisterByte(0x37);
            byte h0Low = humI2C.readRegisterByte(0x36);
            byte h1High = humI2C.readRegisterByte(0x3B);
            byte h1Low = humI2C.readRegisterByte(0x3A);

            //Concatenate to short values
            short h0Full = (short)((h0High << 8 ) | (h0Low & 0xFF));
            short h1Full = (short)((h1High << 8 ) | (h1Low & 0xFF));

            double h0 = (double)h0Full;
            double h1 = (double)h1Full;

            /**
             * Now that we have two points, we can calculate the slope
             * by dividing rise by run
             */
            Double slope = (h1Cal - h0Cal) / (h1 - h0);

            /**
             * And the y-intercept can be determined by isolating for it
             * in the formula (y = mx + b, b = y - mx)
             */
            Double b = h1Cal - (slope * h1);

            /**
             * The value in HOUT represents the independent variable for the above line equation.
             * It is represented by a signed 16-bit value.
             */
            byte hOutHigh = humI2C.readRegisterByte(0x29);
            byte hOutLow = humI2C.readRegisterByte(0x28);

            short hOut = (short)((hOutHigh << 8) | (hOutLow & 0xFF));

            /**
             * Now that we have the equation for our line, and the independent
             * variable represented by tOut, we can calculate our relative humidity
             * (percent)
             */
            humidity = (slope * hOut) + b;

        } catch (Exception e){
            System.out.println(e);
        }
        return humidity;
    }

    /**
     * Function to create a new I2C object
     * 
     * @param id ID for the new I2C object
     * @param _ADDRESS I2C address
     * @return New I2C object
     */
    public static I2C getI2C(String id, int _ADDRESS){
        I2C i2c;

        Context pi4j = Pi4J.newAutoContext();
        I2CProvider i2CProvider = pi4j.provider("linuxfs-i2c");
        I2CConfig i2cConfig = I2C.newConfigBuilder(pi4j).id(id).bus(1).device(_ADDRESS).build();
        i2c = i2CProvider.create(i2cConfig);

        return i2c;
    }

    /**
     * When working with binary strings, this function will
     * fill the appropriate number of leading zeroes
     * @param binString Binary input string
     * @param desiredBits Desired number of bits
     * @return Input string with leading zeroes added to result in the appropriate number of bits
     */
    static public String fillBits(String binString, int desiredBits){
        while(binString.length() < desiredBits){
            binString = '0' + binString;
        }
        return binString;
    }
}
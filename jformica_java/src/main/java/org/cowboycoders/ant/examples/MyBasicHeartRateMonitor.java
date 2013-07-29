package org.cowboycoders.ant.examples;

import org.cowboycoders.ant.Channel;
import org.cowboycoders.ant.NetworkKey;
import org.cowboycoders.ant.Node;
import org.cowboycoders.ant.events.BroadcastListener;
import org.cowboycoders.ant.events.MessageCondition;
import org.cowboycoders.ant.events.MessageConditionFactory;
import org.cowboycoders.ant.interfaces.AntTransceiver;
import org.cowboycoders.ant.messages.SlaveChannelType;
import org.cowboycoders.ant.messages.commands.ChannelRequestMessage;
import org.cowboycoders.ant.messages.commands.ChannelRequestMessage.Request;
import org.cowboycoders.ant.messages.config.ChannelAssignMessage;
import org.cowboycoders.ant.messages.data.BroadcastDataMessage;
import org.cowboycoders.ant.messages.data.ExtendedBroadcastDataMessage;
import org.cowboycoders.ant.messages.responses.ChannelIdResponse;

import javax.usb.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;

class Listener1 implements BroadcastListener<BroadcastDataMessage> {
    @Override
    public void receiveMessage(BroadcastDataMessage message) {
        System.out.println("(" + message.getId() + ") - Heart rate: " + message.getUnsignedData()[7]);
    }

}

class Listener2 implements BroadcastListener<ExtendedBroadcastDataMessage> {
    @Override
    public void receiveMessage(ExtendedBroadcastDataMessage message) {
        System.out.println(message.getDeviceNumber() + ": (" + message.getId() + ") - Heart rate: " + message.getUnsignedData()[7]);
    }
}

class ChannelAssignListener implements BroadcastListener<ChannelAssignMessage> {
    @Override
    public void receiveMessage(ChannelAssignMessage message) {
        System.out.println(message.getChannelNumber() + ": (" + message.getId() + ") - Heart rate: " + message.getBackendMessage());
    }
}

public class MyBasicHeartRateMonitor {

    private static final int HRM_CHANNEL_PERIOD = 8070;
    private static final int HRM_CHANNEL_FREQ = 57;

    private static final boolean HRM_PAIRING_FLAG = false;

    private static final int HRM_TRANSMISSION_TYPE = 0;

    private static final int HRM_DEVICE_TYPE = 120;

    private static final int HRM_DEVICE_ID = 0;
    private static final int HRM_DEVICE_ID1 = 60626;
    private static final int HRM_DEVICE_ID2 = 19186;

    public static final String NETWORK_NAME = "N:ANT+";
    private static final int[] NETWORK_KEY = new int[] {0xB9, 0xA5, 0x21, 0xFB, 0xBD, 0x72, 0xC3, 0x45};

    public static final Level LOG_LEVEL = Level.SEVERE;

    public static void setupLogging() {
        AntTransceiver.LOGGER.setLevel(LOG_LEVEL);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(LOG_LEVEL);
        AntTransceiver.LOGGER.addHandler(handler);
        AntTransceiver.LOGGER.setUseParentHandlers(false);
    }

    public static void printChannelConfig(Channel channel) {

        ChannelRequestMessage msg = new ChannelRequestMessage(channel.getNumber(), Request.CHANNEL_ID);

        MessageCondition condition = MessageConditionFactory.newInstanceOfCondition(ChannelIdResponse.class);

        try {

            ChannelIdResponse response = (ChannelIdResponse) channel.sendAndWaitForMessage(
                    msg, condition, 5L, TimeUnit.SECONDS, null);

            System.out.println();
            System.out.println("Device configuration for channel " + channel.getNumber() + ": ");
            System.out.println("deviceID: " + response.getDeviceNumber());
            System.out.println("deviceType: " + response.getDeviceType());
            System.out.println("transmissionType: " + response.getTransmissionType());
            System.out.println("pairing flag set: " + response.isPairingFlagSet());
            System.out.println();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static void dump(UsbDevice device) {
        UsbDeviceDescriptor desc = device.getUsbDeviceDescriptor();
        if (desc.idVendor() == 0x0fcf) {
            System.out.format("ANTUSB Device = %04x%n", desc.idProduct() & 0xffff);
        }
        if (device.isUsbHub()) {
            UsbHub hub = (UsbHub) device;
            for (Object child : hub.getAttachedUsbDevices()) {
                dump((UsbDevice) child);
            }
        }
    }

    public static void main(String[] args) throws InterruptedException, UsbException {

        setupLogging();

        UsbServices services = UsbHostManager.getUsbServices();
        UsbHub rootHub = services.getRootUsbHub();
        dump(rootHub);
        System.out.println();

        /*
         * Choose driver: AndroidAntTransceiver or AntTransceiver
		 * 
		 * AntTransceiver(int deviceNumber)
		 * deviceNumber : 0 ... number of usb sticks plugged in
		 * 0: first usb ant-stick
		 */
        AntTransceiver antchip = new AntTransceiver(0);

        Node node = new Node(antchip);

        // ANT+ key
        NetworkKey key = new NetworkKey(NETWORK_NAME, NETWORK_KEY);

        node.start();
        node.reset();
        System.out.println("MaxNetworks: " + node.getMaxNetworks());
        System.out.println("MaxChannels: " + node.getMaxChannels());

        node.setNetworkKey(1, key);

        Channel channel;
        while ((channel = createChannel(node, HRM_DEVICE_ID, NETWORK_NAME)) != null) {
            System.out.println("Channel " + channel.getName() + ": " + channel.getNumber());
        }

        Thread.sleep(10000);

        closeChannels(node);

        node.stop();

    }

    private static List<Channel> channels = new ArrayList<Channel>();

    private static Channel createChannel(Node node, int hrmDeviceId, String netKeyName) {
        Channel channel = node.getFreeChannel();

        if (channel != null) {
            channel.setName("C:HRM:" + hrmDeviceId);

            channel.assign(netKeyName, new SlaveChannelType());

            channel.registerRxListener(new Listener1(), BroadcastDataMessage.class);
            channel.registerRxListener(new ChannelAssignListener(), ChannelAssignMessage.class);

            /******* start device specific configuration ******/
            channel.setId(hrmDeviceId, HRM_DEVICE_TYPE, HRM_TRANSMISSION_TYPE, HRM_PAIRING_FLAG);
            channel.setFrequency(HRM_CHANNEL_FREQ);
            channel.setPeriod(HRM_CHANNEL_PERIOD);
            /******* end device specific configuration ******/

            channel.setSearchTimeout(Channel.SEARCH_TIMEOUT_NEVER);

            channel.open();

            channels.add(channel);
        }
        return channel;
    }

    private static void closeChannels(Node node) {
        for (Channel channel : channels) {
            closeChannel(node, channel);
        }
    }

    private static void closeChannel(Node node, Channel channel) {
        channel.close();
        printChannelConfig(channel);
        channel.unassign();
        node.freeChannel(channel);
    }

}

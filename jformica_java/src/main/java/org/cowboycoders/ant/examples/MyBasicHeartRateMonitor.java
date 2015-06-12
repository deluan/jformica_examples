package org.cowboycoders.ant.examples;

import org.cowboycoders.ant.Channel;
import org.cowboycoders.ant.DefaultChannelEventHandler;
import org.cowboycoders.ant.NetworkKey;
import org.cowboycoders.ant.Node;
import org.cowboycoders.ant.events.BroadcastListener;
import org.cowboycoders.ant.interfaces.AntTransceiver;
import org.cowboycoders.ant.messages.SlaveChannelType;
import org.cowboycoders.ant.messages.data.BroadcastDataMessage;

import javax.usb.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;

class Listener1 implements BroadcastListener<BroadcastDataMessage> {
    private String channelName;
    int i;

    public Listener1(String channelName) {
        this.channelName = channelName;
    }

    @Override
    public void receiveMessage(BroadcastDataMessage message) {
        System.out.println("(" + channelName + ") [" + i++ + " - Heart rate: " + message.getUnsignedData()[7]);
    }
}

class EventHandler extends DefaultChannelEventHandler {
    private Channel channel;

    public EventHandler(Channel channel) {
        this.channel = channel;
    }

    @Override
    public void onChannelClosed() {
        System.out.println("(" + channel.getName() + ") - channel closed...");
        MyBasicHeartRateMonitor.closeChannel(channel);
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
    private static final int[] NETWORK_KEY = new int[]{0xB9, 0xA5, 0x21, 0xFB, 0xBD, 0x72, 0xC3, 0x45};

    public static final Level LOG_LEVEL = Level.SEVERE;

    public static void setupLogging() {
        AntTransceiver.LOGGER.setLevel(LOG_LEVEL);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(LOG_LEVEL);
        AntTransceiver.LOGGER.addHandler(handler);
        AntTransceiver.LOGGER.setUseParentHandlers(false);
    }

    private static void dumpAntUsbDevices(UsbDevice device) throws UsbException {
        if (device == null) {
            UsbServices services = UsbHostManager.getUsbServices();
            device = services.getRootUsbHub();
            dumpAntUsbDevices(device);
            System.out.println();
        } else {
            UsbDeviceDescriptor desc = device.getUsbDeviceDescriptor();
            if (desc.idVendor() == 0x0fcf) {
                System.out.format("ANTUSB Device = %04x%n", desc.idProduct() & 0xffff);
            }
            if (device.isUsbHub()) {
                UsbHub hub = (UsbHub) device;
                for (Object child : hub.getAttachedUsbDevices()) {
                    dumpAntUsbDevices((UsbDevice) child);
                }
            }
        }
    }

    public static void main(String[] args) throws InterruptedException, UsbException {

        setupLogging();

        dumpAntUsbDevices(null);

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

//        createChannel(node, HRM_DEVICE_ID, NETWORK_NAME);
        Channel channel;
        while ((channel = createChannel(node, HRM_DEVICE_ID, NETWORK_NAME)) != null) {
            System.out.println("Channel " + channel.getName());
        }

        Thread.sleep(1000000);

        closeChannels();

        node.stop();

    }

    private static List<Channel> channels = new ArrayList<Channel>();

    private static Channel createChannel(Node node, int hrmDeviceId, String netKeyName) {
        Channel channel = node.getFreeChannel();

        if (channel != null) {
            channel.setName("C:HRM:" + channel.getNumber());

            channel.assign(netKeyName, new SlaveChannelType());

            channel.registerRxListener(new Listener1(channel.getName()), BroadcastDataMessage.class);
            channel.registerEventHandler(new EventHandler(channel));

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

    private static void closeChannels() {
        for (Channel channel : channels) {
            closeChannel(channel);
        }
    }

    protected static void closeChannel(Channel channel) {
        Node node = channel.getParent();
        channel.close();
        Utils.printChannelConfig(channel);
        channel.unassign();
        node.freeChannel(channel);
    }

}

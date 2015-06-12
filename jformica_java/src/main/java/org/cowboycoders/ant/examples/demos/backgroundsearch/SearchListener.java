package org.cowboycoders.ant.examples.demos.backgroundsearch;

import org.cowboycoders.ant.Channel;
import org.cowboycoders.ant.ChannelId;
import org.cowboycoders.ant.events.BroadcastListener;
import org.cowboycoders.ant.examples.Utils;
import org.cowboycoders.ant.messages.data.BroadcastDataMessage;

import java.util.Set;
import java.util.concurrent.locks.Lock;

/**
 * This is the listener for the initial search
 * @author will
 *
 */
public class SearchListener implements BroadcastListener<BroadcastDataMessage> {

    private static final int HRM_TRANSMISSION_TYPE = 0;
    private static final int HRM_DEVICE_TYPE = 120;
    private static final ChannelId HRM_WILDCARD_CHANNEL_ID = ChannelId.Builder.newInstance()
            .setDeviceNumber(ChannelId.WILDCARD)
            .setDeviceType(HRM_DEVICE_TYPE)
            .setTransmissonType(HRM_TRANSMISSION_TYPE)
            .build();

    private Channel channel;
    private Set<ChannelId> found;
    private Lock lock;
    private boolean killed = false;

    public void kill() {
        killed = true;
    }

    public SearchListener(Channel channel, Lock channelLock, Set<ChannelId> found) {
        this.channel = channel;
        this.found = found;
        this.lock = channelLock;
    }

    @Override
    public void receiveMessage(BroadcastDataMessage message) {

        // don't block the messenger thread
        new Thread() {
            public void run() {
                doWork();
            }
        }.start();

    }

    private void doWork() {
        try {
            lock.lock();

            if (killed) return;

            ChannelId channelId = Utils.requestChannelId(channel);

            // don't add wildcards to found devices list
            if (channelId.equals(HRM_WILDCARD_CHANNEL_ID)) {
                return;
            }

            // if already in set
            if(!found.add(channelId)) {
                return;
            }

            if (found.size() > 4) {
                System.out.println("reached maximum of 4 devices");
                return;
            }

            // close so we can reset it back to a wildcard
            channel.close();

            System.out.println("found a device: ");
            Utils.printChannelConfig(channel);

            channel.blacklist(found.toArray(new ChannelId[] {}));

            // set back to wildcard
            channel.setId(HRM_WILDCARD_CHANNEL_ID);

            //reopen
            channel.open();


        } finally {
            lock.unlock();
        }
    }

}


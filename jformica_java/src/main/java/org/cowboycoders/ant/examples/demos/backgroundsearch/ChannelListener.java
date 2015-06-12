package org.cowboycoders.ant.examples.demos.backgroundsearch;

import org.cowboycoders.ant.events.BroadcastListener;
import org.cowboycoders.ant.messages.data.BroadcastDataMessage;

public class ChannelListener implements BroadcastListener<BroadcastDataMessage> {

    private String id;

    public ChannelListener(String hrmId) {
        this.id = hrmId;
    }

    /*
     * Once an instance of this class is registered with a channel,
     * this is called every time a broadcast message is received
     * on that channel.
     *
     * (non-Javadoc)
     * @see org.cowboycoders.ant.events.BroadcastListener#receiveMessage(java.lang.Object)
     */
    @Override
    public void receiveMessage(BroadcastDataMessage message) {
            /*
			 * getData() returns the 8 byte payload. The current heart rate
			 * is contained in the last byte.
			 * 
			 * Note: remember the lack of unsigned bytes in java, so unsigned values
			 * should be converted to ints for any arithmetic / display - getUnsignedData()
			 * is a utility method to do this.
			 */
        System.out.println("Heart rate (" + id + "): " + message.getUnsignedData()[7]);
    }

}
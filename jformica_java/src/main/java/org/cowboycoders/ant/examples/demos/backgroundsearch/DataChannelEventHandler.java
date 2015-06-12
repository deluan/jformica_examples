package org.cowboycoders.ant.examples.demos.backgroundsearch;

import org.cowboycoders.ant.Channel;
import org.cowboycoders.ant.DefaultChannelEventHandler;

public class DataChannelEventHandler extends DefaultChannelEventHandler {

    private Channel channel;

    public DataChannelEventHandler(Channel channel) {
        this.channel = channel;
    }

    @Override
    public void onRxFailGoToSearch() {
        System.out.println("Channel #" + channel.getNumber() + " down");
        channel.getParent().freeChannel(channel);
    }

    @Override
    public void onRxSearchTimeout() {
        System.out.println("Channel #" + channel.getNumber() + " timeout");
        channel.getParent().freeChannel(channel);
    }
}

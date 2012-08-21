package net.emiva.openfire.netty;

import org.jboss.netty.channel.Channel;

/**
 * Created with IntelliJ IDEA.
 * User: jcrown
 * Date: 8/13/12
 * Time: 4:45 PM
 * To change this template use File | Settings | File Templates.
 */
public interface Tracker
{
    public void addConnection(Channel ch);
    public void closeAll();
}

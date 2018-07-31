package net.floodlightcontroller.matchingmodule;

import net.floodlightcontroller.restserver.RestletRoutable;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;


public class MACTrackerWebRoutable implements RestletRoutable {
    /**
     * Create the Restlet router and bind to the proper resources.
     */
    @Override
    public Restlet getRestlet(Context context) {
        Router router = new Router(context);

        router.attach("/json", MACTracker.class); /* v2.0 advertised API */
        router.attach("/clear/{switch}/json", MACTracker.class);
        router.attach("/list/{switch}/json", MACTracker.class);
        router.attach("/usage/json", MACTracker.class);
        return router;
    }

    /**
     * Set the base path for the SEP
     */
    @Override
    public String basePath() {
        return "/wm/mactracker";
    }
}

package ccL0;

import edu.berkeley.path.beats.jaxb.Scenario;
import edu.berkeley.path.beats.simulator.JaxbObjectFactory;

/**
 * Created by gomes on 8/13/2014.
 */
public class MPCObjectFactory extends JaxbObjectFactory {
    private String propsFn;
    public MPCObjectFactory(String propsFn){this.propsFn=propsFn;}
    @Override
    public Scenario createScenario() {
        return new MPCScenario(propsFn);
    }
}

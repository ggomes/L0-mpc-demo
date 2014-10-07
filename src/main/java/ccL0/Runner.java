package ccL0;

import edu.berkeley.path.beats.Jaxb;
import edu.berkeley.path.beats.simulator.*;
import matlabcontrol.*;
import matlabcontrol.extensions.MatlabTypeConverter;

import java.io.File;

/**
 * @author Gabriel Gomes (gomes@path.berkeley.edu)
 */
public final class Runner {

    public static void main(String[] args) {
        try {
            if (args.length!=1)
                throw new BeatsException("Incorrect number of input arguments");
            run_simulation(args);
            System.out.println("MPC run complete.");
        } catch (BeatsException e) {
            e.printStackTrace();
        }
        finally {
            if (BeatsErrorLog.hasmessage()) {
                BeatsErrorLog.print();
                BeatsErrorLog.clearErrorMessage();
            }
        }
    }

    private static void run_simulation(String[] args) throws BeatsException{

        MatlabProxy proxy = null;
        MatlabTypeConverter processor = null;

        BeatsProperties props = new BeatsProperties(args[0]);
        MPCScenario scenario = (MPCScenario) Jaxb.create_scenario_from_xml(props.scenario_name,new MPCObjectFactory(args[0]));
        if (scenario==null)
            throw new BeatsException("Scenario did not load");

        // launch matlab
        if(scenario.use_matlab_estimation || scenario.use_matlab_demand_prediction)
            try {
                MatlabProxyFactoryOptions.Builder options = new MatlabProxyFactoryOptions.Builder();
                options.setHidden(false);
                options.setMatlabStartingDirectory(new File(props.getProperty("matlabRoot", "")));
                MatlabProxyFactory factory = new MatlabProxyFactory(options.build());
                proxy = factory.getProxy();
                processor = new MatlabTypeConverter(proxy);
            } catch (MatlabConnectionException e) {
                e.printStackTrace();
            }

        // pass matlab to scenario
        scenario.set_matlab(proxy,processor);

        // initialize and run scenario
        scenario.initialize_with_properties(props);
        scenario.run();

        // disconnect from matlab
        proxy.disconnect();
    }

}

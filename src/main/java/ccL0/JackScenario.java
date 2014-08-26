package ccL0;

import edu.berkeley.path.beats.jaxb.*;
import edu.berkeley.path.beats.simulator.*;
import edu.berkeley.path.beats.simulator.DemandProfile;
import edu.berkeley.path.beats.simulator.DemandSet;
import edu.berkeley.path.beats.simulator.InitialDensitySet;
import edu.berkeley.path.beats.simulator.Link;
import edu.berkeley.path.beats.simulator.Scenario;
import matlabcontrol.*;
import matlabcontrol.extensions.MatlabNumericArray;
import matlabcontrol.extensions.MatlabTypeConverter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Created by gomes on 8/12/14.
 */
public class JackScenario extends Scenario {

    private String propsFn;
    private List<edu.berkeley.path.beats.jaxb.Link> demandLinks;
    private List<edu.berkeley.path.beats.jaxb.Link> sensorLinks;
    private List<edu.berkeley.path.beats.jaxb.Link> allLinks;
    private ArrayList<double []> sensor_measurements;
    private double sim_dt = -1d;
    private double _current_time = -1d;
    private double prev_time = 0d;
    private String matlabRoot;
    private String beatsRoot;
    private MatlabProxy proxy;
    private MatlabTypeConverter processor;

    public JackScenario(String propsFn) {

        this.propsFn = propsFn;

        // load properties file
        Properties props = new Properties();
        try{
            props.load(new FileInputStream(propsFn));
        }
        catch (IOException e){
            System.err.print(e);
        }

        // read properties
//        scenario_name = props.getProperty("SCENARIO","");
        matlabRoot = props.getProperty("matlabRoot", "");
        beatsRoot = props.getProperty("beatsRoot", "");

        try {
            MatlabProxyFactoryOptions.Builder options = new MatlabProxyFactoryOptions.Builder();
            boolean hidden = false;
            options.setHidden(hidden);
            options.setMatlabStartingDirectory(new File(matlabRoot));
            MatlabProxyFactory factory = new MatlabProxyFactory(options.build());
            proxy = null;
            proxy = factory.getProxy();
            processor = new MatlabTypeConverter(proxy);
        } catch (MatlabConnectionException e) {
            e.printStackTrace();
        }
    }

    /* Beats overrides ----------------------------------- */

    @Override
    public void populate() throws BeatsException {
        super.populate();
        sim_dt = getSimdtinseconds();
        allLinks = getLinks();
        demandLinks = getDemandLinks();
        sensorLinks = getSensorLinks();
        sensor_measurements = new ArrayList<double []>();
        saveArray("sensor_ids",getSensorIds());
        saveArray("link_ids",getLinkIds());
        saveArray("link_ids_demand",getDemandLinkIds());

        try {
            proxy.eval("setupBoundaryFlows;");
            //proxy.eval("train_data_set;")
            proxy.eval(String.format("setup_est('%s', '%s')",propsFn, beatsRoot));
        } catch (MatlabInvocationException e) {
            e.printStackTrace();
        }
    }

    @Override
    public InitialDensitySet gather_current_densities() {
        JaxbObjectFactory factory = new JaxbObjectFactory();
        InitialDensitySet init_dens_set = (InitialDensitySet) factory.createInitialDensitySet();
        try {
            double [][] densities = matlabDensities();
            for(int i=0;i<allLinks.size();i++){
                edu.berkeley.path.beats.jaxb.Link link = allLinks.get(i);
                double [] d = densities[i];
                for(int v=0;v<getNumVehicleTypes();v++){
                    Density den = factory.createDensity();
                    den.setLinkId(link.getId());
                    den.setVehicleTypeId(getVehicleTypeIdForIndex(v));
                    den.setContent(BeatsFormatter.csv(d,","));
                    init_dens_set.getDensity().add(den);
                }
            }
        } catch (MatlabInvocationException e) {
            e.printStackTrace();
        }
        return init_dens_set;
    }

    @Override
    public DemandSet predict_demands(double time_current,double sample_dt,double horizon){

        int horizon_steps = (int) Math.round(horizon/sim_dt);
        if(sample_dt!=sim_dt)
            System.err.println("sample_dt!=sim_dt");

        System.out.println("time current: " + time_current);

        // update time variables
        prev_time = _current_time;
        _current_time = time_current;

        // retrieve demands from matlab
        double [][] demands = getMatlabDemands(time_current, horizon_steps);

        // cast as jaxb object
        JaxbObjectFactory factory = new JaxbObjectFactory();
        DemandSet demand_set = (DemandSet) factory.createDemandSet();
        for(int i=0;i<demandLinks.size();i++){
            Link link = (Link) demandLinks.get(i);
            double [] demand = demands[i];
            DemandProfile dp = (DemandProfile) factory.createDemandProfile();
            //DemandProfile demand_profile = link.getDemandProfile();
            demand_set.getDemandProfile().add(dp);
            dp.setLinkIdOrg(link.getId());
            dp.setDt(sim_dt);
            for(int v=0;v<getNumVehicleTypes();v++){
                Demand dem = factory.createDemand();
                dp.getDemand().add(dem);
                dem.setVehicleTypeId(v);
                dem.setContent(BeatsFormatter.csv(demand, ","));
                dem.setContent(BeatsFormatter.csv(demand, ","));
            }
        }
        return demand_set;
    }

    @Override
    public void update() throws BeatsException {
        super.update();

        // collect current sensor measurements
        sensor_measurements.add(get_current_measurements());
    }

    /* Scenario information ------------------------------- */

    private double [][] getLinkIds(){
        List<edu.berkeley.path.beats.jaxb.Link> list = getNetworkSet().getNetwork().get(0).getLinkList().getLink();
        double [][] res = new double[1][list.size()];
        for(int i=0;i<list.size();i++)
            res[0][i] = list.get(i).getId();
        return res;
    }

    private double [][] getSensorIds(){
        List<edu.berkeley.path.beats.jaxb.Sensor> list = getSensorSet().getSensor();
        double [][] res = new double[1][list.size()];
        for(int i=0;i<list.size();i++)
            res[0][i] = list.get(i).getId();
        return res;
    }

    private double [][] getDemandLinkIds(){
        List<edu.berkeley.path.beats.jaxb.DemandProfile> list = getDemandSet().getDemandProfile();
        double [][] res = new double[1][list.size()];
        for(int i=0;i<list.size();i++)
            res[0][i] = list.get(i).getLinkIdOrg();
        return res;
    }

    private List<edu.berkeley.path.beats.jaxb.Link> getLinks(){
        return getNetworkSet().getNetwork().get(0).getLinkList().getLink();
    }

    private List<edu.berkeley.path.beats.jaxb.Link> getDemandLinks(){
        List<edu.berkeley.path.beats.jaxb.DemandProfile> list = getDemandSet().getDemandProfile();
        List<edu.berkeley.path.beats.jaxb.Link> res = new ArrayList<edu.berkeley.path.beats.jaxb.Link>();
        for(int i=0;i<list.size();i++)
            res.add(getLinkWithId(list.get(i).getLinkIdOrg()));
        return res;
    }

    private List<edu.berkeley.path.beats.jaxb.Link> getSensorLinks(){
        List<edu.berkeley.path.beats.jaxb.Sensor> list = getSensorSet().getSensor();
        List<edu.berkeley.path.beats.jaxb.Link> res = new ArrayList<edu.berkeley.path.beats.jaxb.Link>();
        for(int i=0;i<list.size();i++)
            res.add(getLinkWithId(list.get(i).getLinkId()));
        return res;
    }

    private double [] get_current_measurements(){
        double [] meas = new double [sensorLinks.size()];
        for(int i=0;i<sensorLinks.size();i++)
            meas[i] = ((Link)sensorLinks.get(i)).getTotalDensityInVPMeter(0);
        return meas;
    }

    private int get_steps_from_previous_time(){
        return (int) Math.max(1d,Math.min((_current_time - prev_time) / sim_dt, 1000));
    }

    private int get_rounded_previous_time(){
        return (int) (_current_time - get_steps_from_previous_time() * sim_dt);
    }

    private double [][] get_previous_demands(){
        // collect previous points to send to demand predictor
        int n_steps = get_steps_from_previous_time();
        double round_prev_time = get_rounded_previous_time();
        double [][] previous_points = new double[demandLinks.size()][n_steps];
        for(int i=0;i<demandLinks.size();i++){
            previous_points[i] = ((Link)demandLinks.get(i)).getDemandProfile().predict_in_VPS(0, round_prev_time,sim_dt, n_steps);
        }
        return previous_points;
    }

    // row is sensor, column is time, value in vej/meter
    private double [][] get_previous_measurements(){
        if(sensor_measurements.isEmpty())
            return null;
        int numTime = sensor_measurements.size();
        double [][] meas = new double[sensorLinks.size()][numTime];
        for(int i=0;i<numTime;i++)
            for(int j=0;j<sensorLinks.size();j++)
                meas[j][i] = sensor_measurements.get(i)[j];
        return meas;
    }

    private double [][] get_current_time(){
        double [][] c = {{2001,1,10,0,0,_current_time}};
        return c;
    }

    /* Data exchange ---------------------------------------- */

    // time_current ... now in seconds
    // sample_dt ... requested sample time for the profile
    // horizon_steps ... # time steps in the requested profile
    // return matrix of flows in [vph] indexed by [demand link][timestep]
    private double [][]  getMatlabDemands(double time_current,int horizon_steps) {

        // send previous_points, currentTime
        saveArray("previous_points",get_previous_demands());
        saveArray("time_current",get_current_time());

        // tell matlab to process up to current time
        try {
            proxy.eval(String.format("update_detectors(%s, %s, %s, %f)","link_ids_demand", "time_current", "previous_points",sim_dt));
        } catch (MatlabInvocationException e) {
            e.printStackTrace();
        }

        // call demand predictor
        return getCommandResult(String.format("demand_for_beats(%s, %d, %f, %s)","link_ids_demand", horizon_steps,sim_dt, "time_current"));
    }

    private double [][] matlabDensities() throws MatlabInvocationException{

        int round_prev_time = get_rounded_previous_time();

        saveArray("previous_points",get_previous_measurements());
        saveArray("previous_demand_points",get_previous_demands());
        saveArray("time_current",get_current_time());

        if (_current_time > 0) {
            proxy.eval(String.format("update_demand_estimation(%s, %d, %s, %d)","link_ids_demand", round_prev_time, "previous_demand_points", (int) sim_dt));
            proxy.eval(String.format("update_sensor_estimation(%s, %d, %s, %d)","sensor_ids", round_prev_time, "previous_points", (int) sim_dt));
        }
        sensor_measurements.clear();
        return getCommandResult(String.format("give_estimate(%s, %f)","link_ids", _current_time));
    }

    /* Low level JAVA/MATLAB interface --------------------- */

    private void saveArray(String name,double [][] array) {
        if(array==null)
            return;
        try {
            processor.setNumericArray(name, new MatlabNumericArray(array, null));
        } catch (MatlabInvocationException e) {
            e.printStackTrace();
        }
    }

    private double [][] getCommandResult(String command){
        try {
            proxy.eval("tmp = " + command + ";");
            return processor.getNumericArray("tmp").getRealArray2D();
        } catch (MatlabInvocationException e) {
            e.printStackTrace();
        }
        return null;
    }

}

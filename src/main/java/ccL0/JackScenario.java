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

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Created by gomes on 8/12/14.
 */
public class JackScenario extends Scenario {

    private PrintWriter demand_out_file;
    private PrintWriter state_out_file;

    private String demand_prediction_method;
    private boolean use_matlab_demand_prediction;
    private boolean use_matlab_estimation;

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

        try {
            this.demand_out_file = new PrintWriter("out//demands.txt","UTF-8");
            this.state_out_file = new PrintWriter("out//densities.txt","UTF-8");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

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
        demand_prediction_method = props.getProperty("DEMAND_PREDICTION_METHOD", "ZOHnaive").trim();
        use_matlab_demand_prediction = Boolean.parseBoolean(props.getProperty("USE_MATLAB_DEMAND_PREDICTION", "false"));
        use_matlab_estimation = Boolean.parseBoolean(props.getProperty("USE_MATLAB_ESTIMATION", "false"));

        matlabRoot = props.getProperty("matlabRoot", "");
        beatsRoot = props.getProperty("beatsRoot", "");

        if(use_matlab_estimation || use_matlab_demand_prediction)
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

        System.out.println("\tMPC populate()");

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
            if(use_matlab_demand_prediction){
                System.out.println("Matlab:setupBoundaryFlows");
                proxy.eval("setupBoundaryFlows;");
            }
            //proxy.eval("train_data_set;")
            if(use_matlab_estimation){
                System.out.println("Matlab:setup_est");
                proxy.eval(String.format("setup_est('%s', '%s')",propsFn, beatsRoot));
            }
        } catch (MatlabInvocationException e) {
            e.printStackTrace();
        }
    }

    @Override
    public InitialDensitySet gather_current_densities() {
    // calls get_matlab_densities

        System.out.println("\n"+getCurrentTimeInSeconds() + "\tMPC gather_current_densities");
        InitialDensitySet init_dens_set;
        if(!use_matlab_estimation)
            init_dens_set = super.gather_current_densities();
        else{
            JaxbObjectFactory factory = new JaxbObjectFactory();
            init_dens_set = (InitialDensitySet) factory.createInitialDensitySet();
            try {
                double [][] densities = get_matlab_densities();
                for(int i=0;i<allLinks.size();i++){
                    edu.berkeley.path.beats.jaxb.Link link = allLinks.get(i);
                    double d = densities[i][0];
                    Density den = factory.createDensity();
                    den.setLinkId(link.getId());
                    den.setVehicleTypeId(getVehicleTypeIdForIndex(0));
                    den.setContent(String.format("%f",d));
                    init_dens_set.getDensity().add(den);
                }
            } catch (MatlabInvocationException e) {
                e.printStackTrace();
            }
        }
        System.out.println(init_dens_set);
        print_to_state_out(init_dens_set);
        return init_dens_set;
    }

    @Override
    public DemandSet predict_demands(double time_current,double sample_dt,double horizon){
    // calls get_matlab_demands

        DemandSet demand_set;

        System.out.println("\n"+getCurrentTimeInSeconds() + "\tMPC predict_demands");

        if(!use_matlab_demand_prediction && !use_matlab_estimation)
            demand_set = super.predict_demands(time_current,sample_dt,horizon);
        else {                                                                    // why is this needed for estimation?

            int horizon_steps = (int) Math.round(horizon/sim_dt);
            if(sample_dt!=sim_dt)
                System.err.println("sample_dt!=sim_dt");

            // update time variables
            prev_time = _current_time;
            _current_time = time_current;

            // retrieve demands from matlab
            double [][] demands = get_matlab_demands(time_current, horizon_steps);

            // cast as jaxb object
            JaxbObjectFactory factory = new JaxbObjectFactory();
            demand_set = (DemandSet) factory.createDemandSet();
            for(int i=0;i<demandLinks.size();i++){
                Link link = (Link) demandLinks.get(i);
                double [] demand = BeatsMath.times(demands[i],1d/3600d);
                DemandProfile dp = (DemandProfile) factory.createDemandProfile();
                demand_set.getDemandProfile().add(dp);
                dp.setLinkIdOrg(link.getId());
                dp.setDt(sim_dt);
                for(int v=0;v<getNumVehicleTypes();v++){
                    Demand dem = factory.createDemand();
                    dp.getDemand().add(dem);
                    dem.setVehicleTypeId(v);
                    dem.setContent(BeatsFormatter.csv(demand, ","));
                }
            }
        }
        print_to_demand_out(demand_set);
        System.out.println(demand_set);
        return demand_set;
    }

    @Override
    public void update() throws BeatsException {
    // calls get_current_measurements

        if(!use_matlab_estimation){
            super.update();
            return;
        }

        System.out.println("\n"+getCurrentTimeInSeconds() + "\tMPC update()");

        super.update();

        // collect current sensor measurements
        sensor_measurements.add(get_current_measurements());
    }

    @Override
    public void close() throws BeatsException {
        super.close();
        demand_out_file.close();
        state_out_file.close();
    }

    /* Matlab interface ---------------------------------------- */

    private double [][] get_matlab_demands(double time_current, int horizon_steps) {
    // time_current ... now in seconds
    // sample_dt ... requested sample time for the profile
    // horizon_steps ... # time steps in the requested profile
    // return matrix of flows in [vph] indexed by [demand link][timestep]
    // send previous_points, currentTime

    // calls: get_previous_demands_in_us
    //        get_current_time
    //        Matlab:update_detectors
    //        Matlab:demand_for_beats

        saveArray("previous_points",get_previous_demands_in_us());
        saveArray("time_current",get_current_time());

        // tell matlab to process up to current time
        try {
            System.out.println("Matlab:update_detectors");
            proxy.eval(String.format("update_detectors(%s, %s, %s, %f)","link_ids_demand", "time_current", "previous_points",sim_dt));
        } catch (MatlabInvocationException e) {
            e.printStackTrace();
        }

        // call demand predictor
        return getCommandResult(String.format("demand_for_beats(%s, %d, %f, %s, '%s')","link_ids_demand", horizon_steps,sim_dt, "time_current",demand_prediction_method));
    }

    private double [][] get_matlab_densities() throws MatlabInvocationException{
    // calls: get_rounded_previous_time
    //        get_previous_measurements
    //        get_previous_demands
    //        get_current_time
    //        Matlab:update_demand_estimation
    //        Matlab:update_sensor_estimation
    //        Matlab:give_estimate

        System.out.println("\n"+getCurrentTimeInSeconds() + "\tget_matlab_densities()");

        int round_prev_time = get_rounded_previous_time();

        saveArray("previous_points",get_previous_measurements());
        saveArray("previous_demand_points",get_previous_demands());
        saveArray("time_current",get_current_time());

        if (_current_time > 0) {
            System.out.println("Matlab:update_demand_estimation");
            proxy.eval(String.format("update_demand_estimation(%s, %d, %s, %d)","link_ids_demand", round_prev_time, "previous_demand_points", (int) sim_dt));
            System.out.println("Matlab:update_sensor_estimation");
            proxy.eval(String.format("update_sensor_estimation(%s, %d, %s, %d)","sensor_ids", round_prev_time, "previous_points", (int) sim_dt));
        }
        sensor_measurements.clear();
        return getCommandResult(String.format("give_estimate(%s, %f)","link_ids", _current_time));
    }

    private void saveArray(String name,double [][] array) {
        if(array==null || processor==null)
            return;
        try {
            processor.setNumericArray(name, new MatlabNumericArray(array, null));
        } catch (MatlabInvocationException e) {
            e.printStackTrace();
        }
    }

    private double [][] getCommandResult(String command){
        if(proxy==null || processor==null)
            return null;
        System.out.println(getCurrentTimeInSeconds() + "\tgetCommandResult()");

        try {
            System.out.println("Matlab:"+command);
            proxy.eval("tmp = " + command + ";");
            return processor.getNumericArray("tmp").getRealArray2D();
        } catch (MatlabInvocationException e) {
            e.printStackTrace();
        }
        return null;
    }

    /* Output ------------------------------------------- */

    private void print_to_demand_out(DemandSet demand_set) {
        if(demand_out_file==null)
            return;
        for (edu.berkeley.path.beats.jaxb.DemandProfile dp : demand_set.getDemandProfile())
            for (Demand d : dp.getDemand())
                demand_out_file.println(getCurrentTimeInSeconds() + "," + dp.getLinkIdOrg() + "," + d.getContent());
    }

    private void print_to_state_out(edu.berkeley.path.beats.jaxb.InitialDensitySet ids) {
        if(state_out_file==null)
            return;
        for (edu.berkeley.path.beats.jaxb.Density den : ids.getDensity())
            state_out_file.println(getCurrentTimeInSeconds() + "," + den.getLinkId() + "," + den.getContent());
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
        System.out.println("\n"+getCurrentTimeInSeconds() + "\tget_current_measurements()");
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
        for(int i=0;i<demandLinks.size();i++)
            previous_points[i] = ((Link)demandLinks.get(i)).getDemandProfile().predict_in_VPS(0, round_prev_time,sim_dt, n_steps);
        return previous_points;
    }

    private double [][] get_previous_demands_in_us(){
        double [][] demands = get_previous_demands();
        for(int i=0;i<demands.length;i++)
            for(int j=0;j<demands[i].length;j++)
                demands[i][j] *= 3600d;
        return demands;
    }

    private double [][] get_previous_measurements(){
    // row is sensor, column is time, value in veh/meter
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


}

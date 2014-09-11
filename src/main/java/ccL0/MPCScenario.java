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
public class MPCScenario extends Scenario {

    private MatlabProxy proxy;
    private MatlabTypeConverter processor;

    private PrintWriter demand_out_file;
    private PrintWriter state_out_file;

    private String filter_type;
    private String demand_prediction_method;
    public boolean use_matlab_demand_prediction;
    public boolean use_matlab_estimation;
    private String beatsRoot;

    private String propsFn;
    private List<edu.berkeley.path.beats.jaxb.Link> demandLinks;
    private List<edu.berkeley.path.beats.jaxb.Link> sensorLinks;
    private List<edu.berkeley.path.beats.jaxb.Link> allLinks;
    private ArrayList<double []> sensor_measurements;
    private double sim_dt = -1d;
    private double _current_time = -1d;
    private double prev_time = 0d;

    public MPCScenario(String propsFn) {

        this.propsFn = propsFn;

        // open log files
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
        filter_type = props.getProperty("FILTER_TYPE", "particle").trim();
        demand_prediction_method = props.getProperty("DEMAND_PREDICTION_METHOD", "ZOHnaive").trim();
        use_matlab_demand_prediction = Boolean.parseBoolean(props.getProperty("USE_MATLAB_DEMAND_PREDICTION", "false"));
        use_matlab_estimation = Boolean.parseBoolean(props.getProperty("USE_MATLAB_ESTIMATION", "false"));
        beatsRoot = props.getProperty("beatsRoot", "");

    }

    /* Beats overrides -------------------------------------------------------------- */

    @Override
    public void populate() throws BeatsException {
    // cals: Matlab:setupBoundaryFlows
    //       Matlab:setup_est

        super.populate();
        sim_dt = getSimdtinseconds();
        allLinks = getLinks();
        demandLinks = getDemandLinks();
        sensorLinks = getSensorLinks();
        sensor_measurements = new ArrayList<double []>();

        // Matlab globals
        saveArray("sensor_ids",getSensorIds());
        saveArray("link_ids",getLinkIds());
        saveArray("link_ids_demand",getDemandLinkIds());

        // call Matlab setups
        try {
            if(use_matlab_demand_prediction){
                System.out.println("Matlab:setupBoundaryFlows");
                proxy.eval("setupBoundaryFlows;");
            }
            //proxy.eval("train_data_set;")
            if(use_matlab_estimation){
                System.out.println("Matlab:setup_est");
                proxy.eval(String.format("setup_est('%s', '%s','%s')",propsFn, beatsRoot,filter_type));
            }
        } catch (MatlabInvocationException e) {
            e.printStackTrace();
        }
    }

    @Override
    public InitialDensitySet get_current_densities_si() {
    // calls get_matlab_densities_normalized

        InitialDensitySet init_dens_set;
        if(!use_matlab_estimation)
            init_dens_set = super.get_current_densities_si();
        else{
            JaxbObjectFactory factory = new JaxbObjectFactory();
            init_dens_set = (InitialDensitySet) factory.createInitialDensitySet();
            try {
                double [][] densities_normalized = get_matlab_densities_normalized();
                for(int i=0;i<allLinks.size();i++){
                    Link link = (Link) allLinks.get(i);
                    double d_si = densities_normalized[i][0] / link.getLengthInMeters();
                    Density den = factory.createDensity();
                    den.setLinkId(link.getId());
                    den.setVehicleTypeId(getVehicleTypeIdForIndex(0));
                    den.setContent(String.format("%f",d_si));
                    init_dens_set.getDensity().add(den);
                }
            } catch (MatlabInvocationException e) {
                e.printStackTrace();
            }
        }
        print_to_state_out(init_dens_set);
        return init_dens_set;
    }

    @Override
    public DemandSet predict_demands_si(double time_current,double sample_dt,double horizon){
    // calls get_matlab_demands

        DemandSet demand_set;

        if(!use_matlab_demand_prediction && !use_matlab_estimation)
            demand_set = super.predict_demands_si(time_current, sample_dt, horizon);
        else {                                                                    // why is this needed for estimation?

            int horizon_steps = (int) Math.round(horizon/sim_dt);
            if(sample_dt!=sim_dt)
                System.err.println("sample_dt!=sim_dt");

            // update time variables
            prev_time = _current_time;
            _current_time = time_current;

            // retrieve demands from matlab
            double [][] demands_us = get_matlab_demands_us(time_current, horizon_steps);

            // cast as jaxb object
            JaxbObjectFactory factory = new JaxbObjectFactory();
            demand_set = (DemandSet) factory.createDemandSet();
            for(int i=0;i<demandLinks.size();i++){
                Link link = (Link) demandLinks.get(i);
                double [] demand_si = BeatsMath.times(demands_us[i],1d/3600d);
                DemandProfile dp = (DemandProfile) factory.createDemandProfile();
                demand_set.getDemandProfile().add(dp);
                dp.setLinkIdOrg(link.getId());
                dp.setDt(sim_dt);
                for(int v=0;v<getNumVehicleTypes();v++){
                    Demand dem = factory.createDemand();
                    dp.getDemand().add(dem);
                    dem.setVehicleTypeId(v);
                    dem.setContent(BeatsFormatter.csv(demand_si, ","));
                }
            }
        }
        print_to_demand_out(demand_set);
        return demand_set;
    }

    @Override
    public void update() throws BeatsException {
    // calls collect_current_measured_densities_vpmeter

        super.update();

        // collect current sensor measurements
        if(use_matlab_estimation) {
            System.out.println("\n"+getCurrentTimeInSeconds() + "\tMPC update()");
            sensor_measurements.add(collect_current_measured_densities_normalized());
        }
    }

    @Override
    public void close() throws BeatsException {
        super.close();
        demand_out_file.close();
        state_out_file.close();
    }

    /* Matlab interface -------------------------------------------------------------- */

    private double [][] get_matlab_demands_us(double time_current, int horizon_steps) {
    // time_current ... now in seconds
    // sample_dt ... requested sample time for the profile
    // horizon_steps ... # time steps in the requested profile
    // return matrix of flows in [vph] indexed by [demand link][timestep]
    // send previous_points, currentTime

    // calls: get_previous_demands_vph
    //        get_current_time
    //        Matlab:update_detectors
    //        Matlab:demand_for_beats

        saveArray("previous_demands_vph", get_previous_demands_vph());
        saveArray("time_current",get_current_time());

        // tell matlab to process up to current time
        try {
            System.out.println("Matlab:update_detectors");
            proxy.eval(String.format("update_detectors(%s, %s, %s, %f)","link_ids_demand", "time_current", "previous_demands_vph",sim_dt));
        } catch (MatlabInvocationException e) {
            e.printStackTrace();
        }

        // call demand predictor
        return getCommandResult(String.format("demand_for_beats_vph(%s, %d, %f, %s, '%s')","link_ids_demand", horizon_steps,sim_dt, "time_current",demand_prediction_method));
    }

    private double [][] get_matlab_densities_normalized() throws MatlabInvocationException{
    // calls: get_rounded_previous_time
    //        get_collected_densities_vpmeter
    //        get_previous_demands_vps
    //        get_current_time
    //        Matlab:update_demand_estimation
    //        Matlab:update_sensor_estimation
    //        Matlab:give_estimate

        System.out.println("\n"+getCurrentTimeInSeconds() + "\tget_matlab_densities_normalized()");

        int round_prev_time = get_rounded_previous_time();
        saveArray("previous_densities_normalized", get_collected_densities_normalized());
        saveArray("previous_demands_vps", get_previous_demands_vps());
        saveArray("time_current",get_current_time());

        if (_current_time > 0) {
            System.out.println("Matlab:update_demand_estimation");
            proxy.eval(String.format("update_demand_estimation(%s, %d, %s, %d)","link_ids_demand", round_prev_time, "previous_demands_vps", (int) sim_dt));
            System.out.println("Matlab:update_sensor_estimation");
            proxy.eval(String.format("update_sensor_estimation(%s, %d, %s, %d)","sensor_ids", round_prev_time, "previous_densities_normalized", (int) sim_dt));
        }
        sensor_measurements.clear();
        return getCommandResult(String.format("give_estimate(%s, %f)","link_ids", _current_time));
    }

    public void set_matlab(MatlabProxy proxy,MatlabTypeConverter processor) {
        this.proxy = proxy;
        this.processor = processor;
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

    /* Output ----------------------------------------------------------------------- */

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

    /* Scenario information --------------------------------------------------------- */

    public double [][] getLinkIds(){
        List<edu.berkeley.path.beats.jaxb.Link> list = getNetworkSet().getNetwork().get(0).getLinkList().getLink();
        double [][] res = new double[1][list.size()];
        for(int i=0;i<list.size();i++)
            res[0][i] = list.get(i).getId();
        return res;
    }

    public double [][] getSensorIds(){
        List<edu.berkeley.path.beats.jaxb.Sensor> list = getSensorSet().getSensor();
        double [][] res = new double[1][list.size()];
        for(int i=0;i<list.size();i++)
            res[0][i] = list.get(i).getId();
        return res;
    }

    public double [][] getDemandLinkIds(){
        List<edu.berkeley.path.beats.jaxb.DemandProfile> list = getDemandSet().getDemandProfile();
        double [][] res = new double[1][list.size()];
        for(int i=0;i<list.size();i++)
            res[0][i] = list.get(i).getLinkIdOrg();
        return res;
    }

    public List<edu.berkeley.path.beats.jaxb.Link> getLinks(){
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

    private int get_steps_from_previous_time(){
        return (int) Math.max(1d,Math.min((_current_time - prev_time) / sim_dt, 1000));
    }

    private int get_rounded_previous_time(){
        return (int) (_current_time - get_steps_from_previous_time() * sim_dt);
    }

    private double [][] get_previous_demands_vps(){
        // collect previous demands for both predictor and estimator
        int n_steps = get_steps_from_previous_time();
        double round_prev_time = get_rounded_previous_time();
        double [][] previous_points = new double[demandLinks.size()][n_steps];
        for(int i=0;i<demandLinks.size();i++)
            previous_points[i] = ((Link)demandLinks.get(i)).getDemandProfile().predict_in_VPS(0, round_prev_time,sim_dt, n_steps);
        return previous_points;
    }

    private double [][] get_previous_demands_vph(){
        double [][] demands = get_previous_demands_vps();
        for(int i=0;i<demands.length;i++)
            for(int j=0;j<demands[i].length;j++)
                demands[i][j] *= 3600d;
        return demands;
    }

    private double [] collect_current_measured_densities_normalized(){
        System.out.println("\n"+getCurrentTimeInSeconds() + "\tcollect_current_measured_densities_normalized()");
        double [] meas = new double [sensorLinks.size()];
        for(int i=0;i<sensorLinks.size();i++)
            meas[i] = ((Link)sensorLinks.get(i)).getTotalDensityInVeh(0);
        return meas;
    }

    private double [][] get_collected_densities_normalized(){
    // row is sensor, column is time, value in veh/link
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

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
    private List<edu.berkeley.path.beats.jaxb.Link> allLinks;
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
        allLinks = getLinks();
        demandLinks = getDemandLinks();
        saveArray(getSensorIds(), "sensor_ids");
        saveArray(getLinkIds(), "link_ids");
        saveArray(getDemandLinkIds(), "link_ids_demand");

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
    public DemandSet predict_demands(double time_current, double sample_dt, int horizon_steps) {
        System.out.println("beginning");
        System.out.println("time current: " + time_current);
        System.out.println("sample dt: " + sample_dt);
        System.out.println("horizon steps: " + horizon_steps);
        // huge hack, and potentially incorrect!
        //sim_dt = sample_dt;
        prev_time = _current_time;
        _current_time = time_current;
        double [][] demands = getMatlabDemands(time_current, sample_dt, horizon_steps);
        JaxbObjectFactory factory = new JaxbObjectFactory();
        DemandSet demand_set = (DemandSet) factory.createDemandSet();

        for(int i=0;i<demandLinks.size();i++){
            Link link = (Link) demandLinks.get(i);
            double [] demand = demands[i];
            DemandProfile dp = (DemandProfile) factory.createDemandProfile();
            //DemandProfile demand_profile = link.getDemandProfile();
            demand_set.getDemandProfile().add(dp);
            dp.setLinkIdOrg(link.getId());
            dp.setDt(sample_dt);
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

//    private double [][] getSensorLinkIds(){
//        List<edu.berkeley.path.beats.jaxb.Sensor> list = getSensorSet().getSensor();
//        double [][] res = new double[1][list.size()];
//        for(int i=0;i<list.size();i++)
//            res[0][i] = list.get(i).getLinkId();
//        return res;
//    }

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

    /* Data exchange ---------------------------------------- */

    private double [][]  getMatlabDemands(double time_current,double sample_dt,int horizon_steps) {

        int n_steps = (int) Math.max(1,Math.min((time_current - prev_time) / sample_dt, 1000));
        double [][] previous_points = new double[demandLinks.size()][n_steps];
        for(int i=0;i<demandLinks.size();i++){
            double previous_time = time_current - n_steps * sample_dt;
            previous_points[i] = ((Link)demandLinks.get(i)).getDemandProfile().predict_in_VPS(0, previous_time,sample_dt, n_steps);
        }
        double [][] currentTime = {{2001,1,10,0,0,time_current}};
        saveArray(previous_points, "previous_points");
        saveArray(currentTime, "time_current");
        String stringForSensors = "link_ids_demand";
        try {
            proxy.eval(String.format("update_detectors(%s, %s, %s, %d)",stringForSensors, "time_current", "previous_points", (int) sample_dt));
        } catch (MatlabInvocationException e) {
            e.printStackTrace();
        }
        return getCommandResult(String.format("demand_for_beats(%s, %d, %d, %s)",stringForSensors, horizon_steps, (int) sample_dt, "time_current"));
    }

    private double [][] matlabDensities() throws MatlabInvocationException{
        double time_current = _current_time;

        double sample_dt = sim_dt;
        int n_steps = (int) Math.max(1d,Math.min((time_current - prev_time) / sim_dt, 1000));
        double previous_time = time_current - n_steps * sim_dt;
        System.out.println("current: " + time_current);
        System.out.println("previuos" + previous_time);

        // TODO(jackdreilly): This is obviously a stub
        double [][] previous_demand_points = null;
        for(int i=0;i<demandLinks.size();i++){
//            previous_demand_points[i] = ...
        }

        double [][] previous_points = null;
        for(int i=0;i<demandLinks.size();i++)
            previous_points[i] = ((Link)demandLinks.get(i)).getDemandProfile().predict_in_VPS(0, previous_time,sample_dt, n_steps);

        double [][] currentTime = {{2001,1,10,0,0,time_current}};
        saveArray(previous_points, "previous_points");
        saveArray(previous_demand_points, "previous_demand_points");
        saveArray(currentTime, "time_current");
        if (time_current > 0) {
            proxy.eval(String.format("update_demand_estimation(%s, %d, %s, %d)","link_ids_demand", (int) previous_time, "previous_demand_points", (int) sample_dt));
            proxy.eval(String.format("update_sensor_estimation(%s, %d, %s, %d)","sensor_ids", (int) previous_time, "previous_points", (int) sample_dt));
        }
        return getCommandResult(String.format("give_estimate(%s, %d)","link_ids", time_current));
    }

    /* Low level JAVA/MATLAB interface --------------------- */

    private void saveArray(double [][] array,String name) {
        try {
            processor.setNumericArray(name, new MatlabNumericArray(array, null));
        } catch (MatlabInvocationException e) {
            e.printStackTrace();
        }
    }

    private double [][] getCommandResult(String command){
        try {
            proxy.eval("tmp = " + command);
            return processor.getNumericArray("tmp").getRealArray2D();
        } catch (MatlabInvocationException e) {
            e.printStackTrace();
        }
        return null;
    }

}

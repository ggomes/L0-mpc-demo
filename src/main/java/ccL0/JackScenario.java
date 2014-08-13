package ccL0;

import edu.berkeley.path.beats.simulator.*;

import java.util.ArrayList;
import java.util.Properties;

/**
 * Created by gomes on 8/12/14.
 */
public class JackScenario extends Scenario {


    public JackScenario() {

//        Properties props = new Properties();
//        FileReader fr = new FileReader(propsFn);
//        props.load(fr);
//        fr.close();
//        String matlabRoot = props.getProperty("matlabRoot", "/Users/jdr/L0-boundary-flows");
//        String beatsRoot = props.getProperty("beatsRoot", "/Users/jdr/runner/beats");
//        MatlabProxyFactoryOptions options = new MatlabProxyFactoryOptions.Builder();
//        boolean hidden = false;
//        options.setHidden(hidden);
//        options.setMatlabStartingDirectory(new File(matlabRoot));
//        MatlabProxyFactory factory = new MatlabProxyFactory(options.build());
//        val proxy = factory.getProxy;
//        Double [][] sensor_ids = null;
//        Double [][] sensor_link_ids = null;
//        Double [][] demand_link_ids = null;
//        ArrayList<Link> demandLink = null;
//        ArrayList<Link> allLinks = null;
//        Double [][] allLinkIds = null;
//        MatlabTypeConverter processor = new MatlabTypeConverter(proxy);
//        Double _sample_dt = -1d;
//        Double _current_time = -1d;
//        Double prev_time = 0d;
    }

    //
//    def saveArray(array: Array[Array[Double]], name: String) {
//        processor.setNumericArray(name, new MatlabNumericArray(array, null))
//    }

    @Override
    protected void populate() throws BeatsException {
//        super.populate()
//        sensor_ids = Array(sensorSet.getSensor.toList.map{_.getId.toDouble}.toArray)
//        demand_link_ids = Array(demandSet.getDemandProfile.map{_.getLinkIdOrg.toDouble}.toArray)
//        sensor_link_ids = Array(sensorSet.getSensor.toList.map{_.getLinkId.toDouble}.toArray)
//        val net = getNetworkSet.getNetwork.head.asInstanceOf[Network]
//        allLinks = net.getListOfLinks.map{_.asInstanceOf[Link]}
//        allLinkIds = Array(allLinks.map{_.getId.toDouble}.toArray)
//        demandLinks = demand_link_ids.head.map{lid => net.getLinkWithId(lid.toLong)}
//        saveArray(sensor_ids, "sensor_ids")
//        saveArray(allLinkIds, "link_ids")
//        saveArray(demand_link_ids, "link_ids_demand")
//        proxy eval "setupBoundaryFlows;"
//        //proxy eval "train_data_set;"
//        proxy.eval("setup_est('%s', '%s')".format(propsFn, beatsRoot))
    }

//    def matlabDensities = {
//            val time_current = _current_time
//
//            val sample_dt = _sample_dt
//            val n_steps = math.max(1,math.min((time_current - prev_time) / sample_dt, 1000)).toInt
//            val previous_time = time_current - n_steps * sample_dt
//            println("current: " + time_current.toInt)
//            println("previuos" + previous_time.toInt)
//
//            // TODO(jackdreilly): This is obviously a stub
//            val previous_demand_points = demand_link_ids.head.map{lid => {
//            Array.fill(n_steps)(1.0)
//    }}.toArray
//
//    val previous_points = demandLinks.map{link => {
//        link.getDemandProfile.predict_in_VPS(0, previous_time,sample_dt, n_steps)
//    }}.toArray
//    val currentTime = Array(Array(2001,1,10,0,0,time_current))
//    saveArray(previous_points, "previous_points")
//    saveArray(previous_demand_points, "previous_demand_points")
//    saveArray(currentTime, "time_current")
//    if (time_current > 0) {
//        proxy.eval("update_demand_estimation(%s, %d, %s, %d)".format("link_ids_demand", previous_time.toInt, "previous_demand_points", sample_dt.toInt))
//        proxy.eval("update_sensor_estimation(%s, %d, %s, %d)".format("sensor_ids", previous_time.toInt, "previous_points", sample_dt.toInt))
//    }
//    getCommandResult("give_estimate(%s, %d)".format("link_ids", time_current.toInt))
//}
//

    @Override
    public InitialDensitySet gather_current_densities() {
        InitialDensitySet init_dens_set = null;
//        val densities = matlabDensities
//        val factory = new JaxbObjectFactory()
//        val init_dens_set = factory.createInitialDensitySet().asInstanceOf[InitialDensitySet]
//        allLinks.zip(densities).foreach{case (l,d) => {
//        (0 until getNumVehicleTypes).foreach{v => {
//        val den = factory.createDensity()
//        den.setLinkId(l.getId)
//        den.setVehicleTypeId(getVehicleTypeIdForIndex(v))
//        den.setContent(d.toString)
//        init_dens_set.getDensity.add(den)
//        }}
//        }}
//        init_dens_set
        return init_dens_set;
    }


//        def getCommandResult(command: String) = {
//        proxy.eval("tmp = " + command)
//        processor.getNumericArray("tmp").getRealArray2D
//        }
//
//        def getMatlabDemands(time_current: Double, sample_dt: Double, horizon_steps: Int) = {
//        val previous_points = demandLinks.map{link => {
//        val n_steps = math.max(1,math.min((time_current - prev_time) / sample_dt, 1000)).toInt
//        val previous_time = time_current - n_steps * sample_dt
//        link.getDemandProfile.predict_in_VPS(0, previous_time,sample_dt, n_steps)
//        }}.toArray
//        val currentTime = Array(Array(2001,1,10,0,0,time_current))
//        saveArray(previous_points, "previous_points")
//        saveArray(currentTime, "time_current")
//        val stringForSensors = "link_ids_demand"
//        proxy.eval("update_detectors(%s, %s, %s, %d)".format(stringForSensors, "time_current", "previous_points", sample_dt.toInt))
//        getCommandResult("demand_for_beats(%s, %d, %d, %s)".format(stringForSensors, horizon_steps, sample_dt.toInt, "time_current"))
//        }
//

    @Override
    public DemandSet predict_demands(double time_current, double sample_dt, int horizon_steps) {
        DemandSet demand_set=null;
//        println("beginning")
//        println("time current: " + time_current)
//        println("sample dt: " + sample_dt)
//        println("horizon steps: " + horizon_steps)
//        // huge hack, and potentially incorrect!
//        _sample_dt = sample_dt
//        prev_time = _current_time
//        _current_time = time_current
//        val demands = getMatlabDemands(time_current, sample_dt, horizon_steps)
//        val factory = new JaxbObjectFactory()
//        val demand_set = factory.createDemandSet().asInstanceOf[DemandSet]
//        demandLinks.zip(demands).map{case (link, demand) => {
//        val dp = factory.createDemandProfile().asInstanceOf[DemandProfile]
//        val demand_profile = link.getDemandProfile
//        demand_set.getDemandProfile.add(dp)
//        dp.setLinkIdOrg(link.getId)
//        dp.setDt(sample_dt)
//        (0 until getNumVehicleTypes).foreach{i => {
//        val dem = factory.createDemand()
//        dp.getDemand.add(dem)
//        dem.setVehicleTypeId(i)
//        dem.setContent(BeatsFormatter.csv(demand, ","))
//        }}
//        }}
        return demand_set;
    }



}

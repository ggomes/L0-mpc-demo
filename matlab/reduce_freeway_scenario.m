function []=reduce_freeway_scenario(infile,outfile,start_link,internal_link,end_link,start_time,end_time)
% crop a freeway scenario in time and space

if(nargin==0)
    infile = 'C:\Users\gomes\code\L0\L0-mpc-demo\data\210W_pm.xml';
    outfile = 'C:\Users\gomes\code\L0\L0-mpc-demo\data\210W_pm_cropped.xml';
    internal_link = 145;
    start_link = 142;
    end_link = 181;
    start_time = 15;    % [hr]
    end_time = 21;      % [hr]
end

ptr = BeatsSimulation;
ptr.load_scenario(infile);
link_ids = ptr.scenario_ptr.get_link_ids;
linkid_begin_end = ptr.scenario_ptr.link_id_begin_end;

% run beats to obtain internal mainline demand ............................
ptr.run_beats;
start_demand = ptr.inflow_veh{1}(:,link_ids==start_link)*12/3600;

% crop out network ........................................................
selection.boundary = internal_link;
selection.interior = [start_link end_link];

while( ~isempty(selection.boundary) )
    
    add_boundary = [];
    remove_boundary = [];
    add_interior = [];
    
    for i=1:length(selection.boundary)
        
        % get link
        link_id = selection.boundary(i);
        link_ind = link_id==link_ids;
        link = ptr.scenario_ptr.scenario.NetworkSet.network.LinkList.link(link_ind);
        
        % get downstream links
        node = linkid_begin_end(link_ind,3);
        dn_links = linkid_begin_end(linkid_begin_end(:,2)==node,1)';
        
        % get upstream links
        node = linkid_begin_end(link_ind,2);
        up_links = linkid_begin_end(linkid_begin_end(:,3)==node,1)';
        
        bnd_links = setdiff([up_links dn_links],selection.interior);
        
        if(~isempty(bnd_links))
            add_boundary = [add_boundary bnd_links];
        end
        remove_boundary = [remove_boundary link_id];
        add_interior = [add_interior link_id];
        
    end
    selection.interior = unique([selection.interior add_interior]);
    selection.boundary = unique([selection.boundary add_boundary]);
    selection.boundary = setdiff(selection.boundary,remove_boundary);
end

keep_link_ids = selection.interior;
keep_link_ind = ismember(link_ids,keep_link_ids);
keep_node_ids = unique(linkid_begin_end(keep_link_ind,2:3));
keep_node_ind = ismember(ptr.scenario_ptr.get_node_ids,keep_node_ids);

% remove sensors
sensor_link = ptr.scenario_ptr.get_sensor_link_map;
ptr.scenario_ptr.scenario.SensorSet.sensor(~ismember(sensor_link(:,2),keep_link_ids)) = [];

% remove unwanted demands demands
demand_link = ptr.scenario_ptr.get_demandprofile_link_map;
ptr.scenario_ptr.scenario.DemandSet.demandProfile(~ismember(demand_link(:,2),keep_link_ids)) = [];

% add new mainline demand
newdemand = ptr.scenario_ptr.scenario.DemandSet.demandProfile(1);
newdemand.ATTRIBUTE.id = -1;
newdemand.ATTRIBUTE.link_id_org = start_link;
newdemand.demand.CONTENT = start_demand;
ptr.scenario_ptr.scenario.DemandSet.demandProfile(end+1) = newdemand;

% remove fds
fd_link = ptr.scenario_ptr.get_fd_link_map;
ptr.scenario_ptr.scenario.FundamentalDiagramSet.fundamentalDiagramProfile(~ismember(fd_link(:,2),keep_link_ids)) = [];

% remove nodes and links
ptr.scenario_ptr.scenario.NetworkSet.network.LinkList.link(~keep_link_ind) = [];
ptr.scenario_ptr.scenario.NetworkSet.network.NodeList.node(~keep_node_ind) = [];

% from remaining nodes, remove non-existent inputs and outputs
bad_nodes = [];
for i=1:length(ptr.scenario_ptr.scenario.NetworkSet.network.NodeList.node)
    
    node = ptr.scenario_ptr.scenario.NetworkSet.network.NodeList.node(i);
    
    % inputs
    keep_in = [];
    if(~isempty(node.inputs))
        keep_in = ismember(cellfun(@(x) x.link_id, {node.inputs.input.ATTRIBUTE}),keep_link_ids);
        ptr.scenario_ptr.scenario.NetworkSet.network.NodeList.node(i).inputs.input(~keep_in)=[];
    end
    
    % outputs
    keep_out = [];
    if(~isempty(node.outputs))
        keep_out = ismember(cellfun(@(x) x.link_id, {node.outputs.output.ATTRIBUTE}),keep_link_ids);
        ptr.scenario_ptr.scenario.NetworkSet.network.NodeList.node(i).outputs.output(~keep_out)=[];
    end
    
    if(any([~keep_in ~keep_out]))
        bad_nodes(end+1) = node.ATTRIBUTE.id;
    end
end

% remove splits
split_node = ptr.scenario_ptr.get_split_node_map;
keep_nodes_splits = setdiff(keep_node_ids,bad_nodes);
ptr.scenario_ptr.scenario.SplitRatioSet.splitRatioProfile(~ismember(split_node(:,2),keep_nodes_splits)) = [];

% remove split ratio profiles for bad nodes
split_node = ptr.scenario_ptr.get_split_node_map;
ptr.scenario_ptr.scenario.SplitRatioSet.splitRatioProfile(~ismember(split_node(:,2),keep_node_ids)) = [];

% reduce size of profiles .................................................

start_ind = start_time*12;
end_ind = end_time*12;
discard = true(1,288);
discard(start_ind:end_ind) = false;

for i=1:length(ptr.scenario_ptr.scenario.DemandSet.demandProfile)
    ptr.scenario_ptr.scenario.DemandSet.demandProfile(i).demand.CONTENT(discard) = [];
end

for i=1:length(ptr.scenario_ptr.scenario.SplitRatioSet.splitRatioProfile)
    srp = ptr.scenario_ptr.scenario.SplitRatioSet.splitRatioProfile(i);
    for j=1:length(srp.splitratio)
        if(length(srp.splitratio(j).CONTENT)==288)
            ptr.scenario_ptr.scenario.SplitRatioSet.splitRatioProfile(i).splitratio(j).CONTENT(discard) = [];
        end
    end
end

ptr.scenario_ptr.save(outfile);

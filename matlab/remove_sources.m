function []=remove_sources(in_file,out_file)

% remove source links and reattach demands
if(nargin==0)
    in_file = 'C:\Users\gomes\code\L0\L0-mpc-demo\data\210W_pm_cropped.xml';
    out_file = 'C:\Users\gomes\code\L0\L0-mpc-demo\data\210W_pm_cropped_L0.xml';
end

ptr = ScenarioPtr;
ptr.load(in_file);

is_source = find(strcmp(ptr.get_link_types,'Source'));
link_id_begin_end = ptr.link_id_begin_end;

% attach demands to downstream links
demand2link = ptr.get_demandprofile_link_map;
for i=1:length(is_source)
    
    source_link_id = link_id_begin_end(is_source(i),1);
    
    begin_node(i) = link_id_begin_end(is_source(i),2);
    end_node(i) = link_id_begin_end(is_source(i),3);
    next_link_ind = find(link_id_begin_end(:,2)==end_node(i));
    if(length(next_link_ind)~=1)
        error('123123')
    end
    next_link_id = link_id_begin_end(next_link_ind,1);
    
    demand_ind = demand2link(:,2)==source_link_id;
    
    if(~any(demand_ind))
        error('123123')
    end
    
    ptr.scenario.DemandSet.demandProfile(demand_ind).ATTRIBUTE.link_id_org = next_link_id;
    
end

% remove source links
ptr.scenario.NetworkSet.network.LinkList.link(is_source) = [];

% remove references in nodes
node_ids = ptr.get_node_ids;
for i=1:length(end_node)
    node_ind = end_node(i)==node_ids;
    node=ptr.scenario.NetworkSet.network.NodeList.node(node_ind);
    node.inputs=[];
    node.node_type.ATTRIBUTE.id = 6;
    node.node_type.ATTRIBUTE.name = 'Terminal';
    ptr.scenario.NetworkSet.network.NodeList.node(node_ind) = node;
end

% remove begin nodes
ptr.scenario.NetworkSet.network.NodeList.node(ismember(node_ids,begin_node))=[];

ptr.save(out_file);

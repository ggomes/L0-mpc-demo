function []=add_adjoint(config_file,out_file,queue_limit,policy_maker_horizon,add_events)

if(nargin==0)
    config_file = 'C:\Users\gomes\code\L0\L0-mpc-demo\data\210W_pm_cropped_L0.xml';
    out_file = 'C:\Users\gomes\code\L0\L0-mpc-demo\data\210W_pm_adjoint.xml';
    queue_limit = 30;   % vehicles
    policy_maker_horizon = 600;
    add_events = false;
end

ptr = ScenarioPtr;
ptr.load(config_file);

has_queue_override = ~isinf(queue_limit);

link_types = ptr.get_link_types;
link_ids = ptr.get_link_ids;
is_onramp = strcmp(link_types,'On-Ramp');
num_onramps = sum(is_onramp);
onramp_ids = link_ids(is_onramp);

if(has_queue_override)
    p_max_queue_vehicles = generate_mo('parameter');
    p_max_queue_vehicles.ATTRIBUTE.name = 'max_queue_vehicles';
    p_max_queue_vehicles.ATTRIBUTE.value = nan;
else
    p_max_queue_vehicles = [];
end
p_max_rate = generate_mo('parameter');
p_max_rate.ATTRIBUTE.name = 'max_rate_in_vphpl';
p_max_rate.ATTRIBUTE.value = 900;
p_min_rate = generate_mo('parameter');
p_min_rate.ATTRIBUTE.name = 'min_rate_in_vphpl';
p_min_rate.ATTRIBUTE.value = 240;
a_actuator = struct( ...
    'ATTRIBUTE',struct('id',nan) , ...
    'scenarioElement', struct('ATTRIBUTE',struct('id',nan,'type','link')) , ...
    'actuator_type', struct('ATTRIBUTE',struct('id',0,'name','ramp_meter')) , ...
    'parameters',struct('parameter',[p_max_queue_vehicles p_max_rate p_min_rate]),...
    'queue_override', struct('ATTRIBUTE',struct('strategy','max_rate')) );

if(~has_queue_override)
    a_actuator = rmfield(a_actuator,{'queue_override'});
end
actuators = repmat(a_actuator,1,num_onramps);
clear a_actuator

a_controller = generate_mo('controller',true);
a_controller = rmfield(a_controller,{'ActivationIntervals','table','feedback_sensors'});

a_controller.ATTRIBUTE = rmfield(a_controller.ATTRIBUTE,{'name','mod_stamp','crudFlag'});

a_controller.ATTRIBUTE.dt = 300;
a_controller.ATTRIBUTE.id = 0;
a_controller.ATTRIBUTE.name = 'MPC';
a_controller.ATTRIBUTE.type = 'CRM_MPC';
a_controller.ATTRIBUTE.enabled = 'true';

a_controller.parameters = rmfield(a_controller.parameters,{'description','ATTRIBUTE'});
a_parameter = struct('ATTRIBUTE',struct('name','','value',nan));
a_controller.parameters.parameter = repmat(a_parameter,1,1);
a_controller.parameters.parameter(1).ATTRIBUTE.name = 'policy';
a_controller.parameters.parameter(1).ATTRIBUTE.value = 'adjoint';
a_controller.parameters.parameter(2).ATTRIBUTE.name = 'dt_optimize';
a_controller.parameters.parameter(2).ATTRIBUTE.value = 300;
a_controller.parameters.parameter(3).ATTRIBUTE.name = 'policy_maker_timestep';
a_controller.parameters.parameter(3).ATTRIBUTE.value = 5;
a_controller.parameters.parameter(4).ATTRIBUTE.name = 'policy_maker_horizon';
a_controller.parameters.parameter(4).ATTRIBUTE.value = policy_maker_horizon;
a_controller.target_actuators.target_actuator = struct('ATTRIBUTE',struct('id',nan,'usage',nan));
controllers = a_controller;

clear a_controller

% events
if(add_events)
    a_event = struct( 'ATTRIBUTE' , struct('id',nan,'tstamp',nan,'enabled','true','type','global_control_toggle') , ...
        'parameters', struct('parameter',generate_mo('parameter') ) );
    a_event.parameters.parameter.ATTRIBUTE.name = 'on_off_switch';
    a_event.parameters.parameter.ATTRIBUTE.value = '';
    
    EventSet = struct( 'ATTRIBUTE', struct('id',0,'project_id',0) , ...
        'event',repmat(a_event,1,5) );
    
    % off at midnight
    EventSet.event(1).ATTRIBUTE.id = 0;
    EventSet.event(1).ATTRIBUTE.tstamp = 0;
    EventSet.event(1).parameters.parameter.ATTRIBUTE.value = 'off';
    
    % on at 6am
    EventSet.event(2).ATTRIBUTE.id = 1;
    EventSet.event(2).ATTRIBUTE.tstamp = 21600;
    EventSet.event(2).parameters.parameter.ATTRIBUTE.value = 'on';
    
    % off at 9am
    EventSet.event(3).ATTRIBUTE.id = 2;
    EventSet.event(3).ATTRIBUTE.tstamp = 32400;
    EventSet.event(3).parameters.parameter.ATTRIBUTE.value = 'off';
    
    % on at 3pm
    EventSet.event(4).ATTRIBUTE.id = 3;
    EventSet.event(4).ATTRIBUTE.tstamp = 54000;
    EventSet.event(4).parameters.parameter.ATTRIBUTE.value = 'on';
    
    % off at 7pm
    EventSet.event(5).ATTRIBUTE.id = 4;
    EventSet.event(5).ATTRIBUTE.tstamp = 68400;
    EventSet.event(5).parameters.parameter.ATTRIBUTE.value = 'off';
end

for i=1:num_onramps
    
    % actuators
    actuators(i).ATTRIBUTE.id = onramp_ids(i);
    actuators(i).scenarioElement.ATTRIBUTE.id = onramp_ids(i);
    if(has_queue_override)
        actuators(i).parameters.parameter(1).ATTRIBUTE.value = queue_limit;
    end
    
    % controllers
    controllers.target_actuators.target_actuator(i).ATTRIBUTE.id = onramp_ids(i);
    controllers.target_actuators.target_actuator(i).ATTRIBUTE.usage = 'onramp';
end

ptr.scenario.ActuatorSet = struct('ATTRIBUTE',struct('project_id',0,'id',0));
ptr.scenario.ActuatorSet.actuator = actuators;
clear actuators

ptr.scenario.ControllerSet = struct('ATTRIBUTE',struct('project_id',0,'id',0));
ptr.scenario.ControllerSet.controller = controllers;
clear controllers

if(add_events)
    ptr.scenario.EventSet = EventSet;
    clear EventSet
end

ptr.save(out_file);



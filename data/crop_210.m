% cut midnight-3pm profiles from 210W

clear

ptr = ScenarioPtr;
ptr.load('210W_pm.xml');

start_time = 15;    % [hr]
end_time = 21;      % [hr]

start_ind = start_time*12;
end_ind = end_time*12;
discard = true(1,288);
discard(start_ind:end_ind) = false;

for i=1:length(ptr.scenario.DemandSet.demandProfile)
    ptr.scenario.DemandSet.demandProfile(i).demand.CONTENT(discard) = [];
end

for i=1:length(ptr.scenario.SplitRatioSet.splitRatioProfile)
    srp = ptr.scenario.SplitRatioSet.splitRatioProfile(i);
    for j=1:length(srp.splitratio)
        if(length(srp.splitratio(j).CONTENT)==288)
            ptr.scenario.SplitRatioSet.splitRatioProfile(i).splitratio(j).CONTENT(discard) = [];
        end
    end
end

ptr.save('210W_pm_cropped.xml');

disp('done')
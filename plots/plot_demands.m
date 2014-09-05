clear

root = fileparts(fileparts(mfilename('fullpath')));

% read output from mpc runner
demands_out = load(fullfile(root,'out','demands_armax.txt'));

% t = unique(demands_out(:,1));
links = unique(demands_out(:,2));
for i=1:length(links)
    ind = demands_out(:,2)==links(i);
    demands(i).link = links(i);
    demands(i).start_time = demands_out(ind,1);
    demands(i).flow = demands_out(ind,3:end)*3600;
end
clear t links ind demands_out

% read inputs from mat file
load('data_from_toy_network')
for i=1:length(demands)
    ind = demands(i).link==detectorID;
    demands(i).historical_flow = flow(1:288,ind);
end

% plot

dt = 5;
for i=1:length(demands)
    N = size(demands(i).flow,2);
    figure    
    for j=1:size(demands(i).start_time)        
        start_time = demands(i).start_time(j);
        end_time = start_time + dt*N;
        t = start_time:dt:end_time-dt;
        plot(t,demands(i).flow(j,:),'Color',rand(1,3),'LineWidth',2)
        hold on
    end
    plot(0:300:86100,demands(i).historical_flow,'ko','LineWidth',2)
    set(gca,'xtick',0:300:86400)
    title(['link ' num2str(demands(i).link)])
    grid
    set(gca,'XLim',[0 7200])
end

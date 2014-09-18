clear
close all
clear

[P,T] = get_properties_files();

X = repmat(struct('time',[],'tvh',[],'tvm',[]),1,length(P));
for p=1:length(P)
    prop_file = P{p};
    ptr(p) = BeatsSimulation;
    ptr(p).load_properties(prop_file);
    [X(p).time,X(p).tvh,X(p).tvm]=ptr(p).compute_performance;
end

not_there = cellfun(@isempty,{X.time});
X(not_there)=[];
P(not_there)=[];
T(not_there)=[];
ptr(not_there)=[];
clear not_there 

time = X(1).time;
TVH = [X.tvh];
TVM = [X.tvm];

figure('Position',[ 403    62   866   604])
plot(time,TVH,'LineWidth',2);
title('TVH');
set(gca,'XLim',[time(1),time(end)])
grid
for i=1:length(T)
   L(i) = {[T{i} ' (' num2str(sum(TVH(:,i)),'%.0f') ' veh.hr)']};
end
legend(L,'Location','SouthWest')


figure('Position',[ 403    62   866   604])
plot(time,TVM,'LineWidth',2);
title('TVM');
set(gca,'XLim',[time(1),time(end)])
grid
for i=1:length(T)
   L(i) = {[T{i} ' (' num2str(sum(TVM(:,i)),'%.0f') ' veh.mile)']};
end
legend(L,'Location','SouthWest')

for i=1:length(ptr)
%     ptr(i).plot_freeway_contour;
    ptr(i).plot_source_queues
end
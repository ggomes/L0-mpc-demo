% clear

root = fileparts(fileparts(mfilename('fullpath')));
ml_ids = [198 199 200 202 203 100 204];

% read output from mpc runner
densities_out = load(fullfile(root,'out','densities_1.txt'));

time = unique(densities_out(:,1));

D = cell2mat(cellfun(@(x) densities_out(densities_out(:,2)==x,3),num2cell(ml_ids) ,'UniformOutput',false));
D = [D;D(end,:)];
D = [D D(:,end)];

h=pcolor(D);
set(h,'EdgeAlpha',0)
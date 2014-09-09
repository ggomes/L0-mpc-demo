clear 
close all
% modified by Cheng-Ju 8/27/2014
alinea_gain_value = 50.0 * 1609.344 / 3600.0;  % in meter/sec
queue_limit = 30;   % vehicles

root = fileparts(fileparts(mfilename('fullpath')));
config_file = 'C:\BeATS_workspace\I-210W_prepare_for_demo\j.xml';

%% I-210W

% rm with upstream feedback / no override
i = 1;
X(i).folder_base        = fullfile(root);
X(i).config_file        = fullfile(config_file);
X(i).folder_rm          = fullfile(root);
X(i).out_file           = fullfile(X(i).folder_rm,'rm_nooverride_up.xml');
X(i).up_or_down         = 'up';
X(i).alinea_gain_value  = alinea_gain_value;
X(i).queue_limit        = inf;

% rm with upstream feedback / with override
i = i+1;
X(i).folder_base        = fullfile(root);
X(i).config_file        = fullfile(config_file);
X(i).folder_rm          = fullfile(root);
X(i).out_file           = fullfile(X(i).folder_rm,'rm_override_up.xml');
X(i).up_or_down         = 'up';
X(i).alinea_gain_value  = alinea_gain_value;
X(i).queue_limit        = queue_limit;
            
% rm with downstream feedback / no override
i = i+1;
X(i).folder_base        = fullfile(root);
X(i).config_file        = fullfile(config_file);
X(i).folder_rm          = fullfile(root);
X(i).out_file           = fullfile(X(i).folder_rm,'rm_nooverride_down.xml');
X(i).up_or_down         = 'down';
X(i).alinea_gain_value  = alinea_gain_value;
X(i).queue_limit        = inf;
            
% rm with downstream feedback / with override
i = i+1;
X(i).folder_base        = fullfile(root);
X(i).config_file        = fullfile(config_file);
X(i).folder_rm          = fullfile(root);
X(i).out_file           = fullfile(X(i).folder_rm,'rm_override_down.xml');
X(i).up_or_down         = 'down';
X(i).alinea_gain_value  = alinea_gain_value;
X(i).queue_limit        = queue_limit;

%% run generate_rm ....................................................
for i=1:length(X)
    generate_rm( X(i).config_file , ...
                     X(i).out_file , ...
                     X(i).up_or_down , ...
                     X(i).alinea_gain_value , ...
                     X(i).queue_limit,...
                     'adjoint')
end


disp('done')
function script_run_all()

for p=get_properties_files()
    runbeats(p{1})
end

disp('done')

function []=runbeats(prop)
beatsjar = 'C:\Users\gomes\code\L0\beats\target\beats-0.1-SNAPSHOT-jar-with-dependencies.jar';
system(['java -jar ' beatsjar ' -s ' prop])

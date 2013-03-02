desc "run lein sawnk on port"
task :swank do
  port = File.readlines(".swank.port").first
  port.chomp!
  system "lein", "swank", port
end

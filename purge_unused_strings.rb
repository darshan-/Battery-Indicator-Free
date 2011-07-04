#!/usr/bin/env ruby

require 'nokogiri'

string_names = {}
string_files = []

`grep R.string src/com/darshancomputing/BatteryIndicator/*.java`.each_line do |s|
  string_names[s.match(/R.string.([a-z_]*)/)[1]] = true
end

`grep -R --exclude-dir=.svn @string res AndroidManifest.xml`.each_line do |s|
  string_names[s.match(/@string\/([a-z_]*)/)[1]] = true
end

Dir.glob('res/values*').each do |d|
  file = d << "/strings.xml"
  string_files << file if File.exists?(file)
end
string_files.sort!

string_files.each do |file|
  strings_xml = ""
  File.open(file) {|f| strings_xml = f.read()}

  doc = Nokogiri::XML(strings_xml)
end

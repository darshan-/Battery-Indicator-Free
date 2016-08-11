#!/usr/bin/env ruby

# TODO: also purge plurals and string-arrays

require 'nokogiri'

string_names = {}
string_files = []

data = [{:matches => `grep R.string src/com/darshancomputing/BatteryIndicator/*.java`,
          :regex => /R.string.([a-z0-9_]*)/},
        {:matches => `grep -R --exclude-dir=.svn @string res AndroidManifest.xml`,
          :regex => /@string\/([a-z0-9_]*)/}]

data.each do |set|
  set[:matches].each_line do |s|
    r = set[:regex]

    while(true)
      m = s.match(r)
      break if not m

      string_names[m[1]] = true
      s = m.post_match
    end
  end
end

Dir.glob('res/values*').each do |d|
  file = d << "/strings.xml"
  string_files << file if File.exists?(file)
end
string_files.sort!

string_files.each do |file|
  puts "Editing " << file

  strings_xml = ""
  File.open(file) {|f| strings_xml = f.read()}

  doc = Nokogiri::XML(strings_xml)

  doc.xpath('//string').each do |str_el|
    if not string_names[str_el.attr('name')] then
      puts " - Deleting " << str_el.attr('name')
      str_el.remove()
    end
  end

  s = doc.to_xml(:encoding => 'utf-8')
  s = s.gsub(/(\n\s+\n)+/, "\n")

  File.open(file, 'w') {|f| f.write(s)}
end

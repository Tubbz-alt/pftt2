
require 'abstract_class'
require 'iconv'

module Diff
  module Engine
      
  class BaseDiff
    #abstract
    attr_accessor :_diff # TODO 
    def initialize( om, expected, actual, test_ctx, host, middleware, scn_set, php )
      @om = om
      @expected = expected
      @actual = actual
      @test_ctx = test_ctx
      @host = host
      @middleware = middleware
      @scn_set = scn_set
      @php = php
      
      # TODO character encoding
      #  also an issue in _compare_line()
      # @iconv = Iconv.new('US-ASCII//IGNORE', 'UTF-8') 
    end
    
    def self.from_xml(xml)
      # TODO TUE
      d = Diff::Engine::Formatted::Php5.new(Diff::OverrideManager.new(), '', '', nil, nil, nil, nil, nil)
      d._diff = []
        xml.each do |x|
      x.map do |tag_name, chunks|
        chunks.each do |chunk|
          case tag_name
          when 'delete'
            d._diff.push([:delete, chunk['text'], chunk['@line'], nil])
          when 'insert'
            d._diff.push([:insert, nil, chunk['@line'], chunk['text']])
          when 'equals'
            d._diff.push([:equals, chunk['text'], chunk['@line'], nil])
          end
        end
      end
      end
      return d
    end
    
    def to_xml(serial)
      equals = []
      delete = []
      insert = []
      chunk_text = ''
      last_chunk_type = nil
      diff().each do |line|
                
        chunk_type = line[0]
        text = (chunk_type==:insert)?line[3]:line[1]
        if text.nil?
          next
        end
        
    # TODO convert to UTF-8
        text = Iconv.conv('UTF-8//IGNORE', 'UTF-8', text)
        
        # merge contiguous chunks of same type
        if (last_chunk_type.nil? or last_chunk_type == chunk_type) and chunk_text
          chunk_text += text
#          if chunk_text
#            chunk_text += text
#          else
#chunk_text = text
#          end
        else
          if chunk_text
            # TODO TUE
            if false#@om.ignore?(Chunk.new(chunk_text, 'file_name', 0, 0, last_chunk_type))
              chunk_text = ''
            else
              serial.startTag(nil, (case last_chunk_type
              when :delete
                'delete'
              when :insert
                'insert'
              when :equals
                'equals'
              end))
              
              serial.text(chunk_text)
              
              serial.endTag(nil, (case last_chunk_type
              when :delete
                'delete'
              when :insert
                'insert'
              when :equals
                'equals'
              end))
                          
            end
          end
          
          chunk_text = text
          
        end
        last_chunk_type = chunk_type
      end
    end # def to_xml

    def to_s
      @s ||= diff.map do |line|
        case line[0]
        when :insert then '+'
        when :delete then '-'
        else ''
        # use iconv to fix character encoding problems
        # TODO end + (@iconv.conv(String.not_nil(line[(line[0]==:delete)?1:3])).gsub(/\n\Z/,''))
      end + ((String.not_nil(line[(line[0]==:delete)?1:3])).gsub(/\n\Z/,''))
      end.join("\n")
    end

    def stat
      {
        :inserts=>0,
        :deletes=>0
      }
    end

    def match?
      @match ||= changes.zero?
    end

    def changes
      @changes ||= diff.count{|token| next true unless token[0]==:equals}
    end
    
    protected

    def diff
      @_diff ||= _get_diff( 
        @expected.lines.map{|i|i}, # we want to keep the separator
        @actual.lines.map{|i|i}    # so we can't just split
      )
    end
    
    protected
    
    # This method gets replaced by sub-classes and is the part that does the actual
    # comparrisons.
    def _compare_line( expectation, result )
      expectation == result or (result.rstrip!=nil and expectation.rstrip.chomp == result.rstrip.chomp)
    end

    def _get_diff( expectation, result )
      prefix = _common_prefix( expectation, result )
      if prefix.length.nonzero?
        expectation.shift prefix.length
        result.shift prefix.length
      end

      suffix = _common_suffix( expectation, result )
      if suffix.length.nonzero?
        expectation.pop suffix.length
        result.pop suffix.length
      end

      return (
        _tokenize( prefix, :equals ) +
        _diff_engine( expectation, result )+
        _tokenize( suffix, :equals )
      )
    end

    def _diff_engine( expectation, result )
      return _tokenize( result, :insert ) if expectation.empty?
      return _tokenize( expectation, :delete ) if result.empty?

      case
      when expectation.length < result.length
        # test to see if the expectation is *inside* the result
        start = 0
        while start+expectation.length <= result.length
          return(
            _tokenize( result.first( start ), :insert ) +
            _tokenize( result.slice( start, expectation.length ), :equals )
            _tokenize( result.dup.drop( start + expectation.length  ), :insert )
          ) if _compare_lines( expectation, result.slice( start, expectation.length ) )
          start +=1
        end
      when result.length < expectation.length 
        # test to see if the result is *inside* the expectation
        start = 0
        while start+result.length <= expectation.length
          return(
            _tokenize( expectation.first( start ), :insert ) +
            _tokenize( result, :equals )
            _tokenize( expectation.dup.drop( start + result.length  ), :insert )
          ) if _compare_lines( expectation.slice( start, result.length ), result )
          start +=1
        end
      end
      
      lcs_max = 500
      chunk_size = 50

      if (expectation.length + result.length) < lcs_max
        # try using LCS
        line_diffs = _diff_lcs( expectation, result)
      elsif [expectation.length,result.length].min > chunk_size
        # chunk off a bit & try again
        line_diffs = _reduce_noise(
          _get_diff( expectation.first(chunk_size), result.first(chunk_size) )+
          _get_diff( expectation.dup.drop(chunk_size), result.dup.drop(chunk_size) )
        )
      else
        # last resort.
        line_diffs = _tokenize( expectation, :delete ) + _tokenize( result, :insert )
      end
      
#      $temp_console_lock.synchronize do
#        puts "expectation"
#      puts expectation
#      puts "result"
#      puts result
#      line_diffs.each do |line_info|
#        puts line_info.inspect
#      end
#      STDIN.gets() # wait for input TODO THU
#      end
#      #
#      # if in interactive mode, prompt the user to debug each diff
#      chunk_replacement = {}
#      if TODO $interactive_mode
#        line_diffs.each{|line_info|
#          if line_info[0] == :delete or line_info[0] == :insert
#            diff_type = line_info[0]
#            expect_line = line_info[1]
#            line_num = line_info[2]
#            actual_line = line_info[3]
#              
#            # break up differences within the line into individual changes
#            # and then prompt the user for them
#            dlm = DiffLineManager.new(line_num, expect_line, actual_line)
#                        
#            #
#            @test_ctx.semaphore4.synchronize {
#              while dlm.get_next
#                if @test_ctx.chunk_replacement.has_key?(dlm.chunk)
#                  dlm.replace(@test_ctx.chunk_replacement[dlm.chunk])
#                elsif chunk_replacement.has_key?(dlm.chunk)
#                  dlm.replace(chunk_replacement[dlm.chunk])
#                else
#                  while not prompt(chunk_replacement, dlm)
#                    # keep prompting if we're supposed to
#                  end
#                  
#                  if dlm.skip_file or dlm.skip_line
#                    break
#                  end
#                end
#              end
#            }
#            if dlm.diff.empty?
#              line_diffs.delete
#            end
#            if dlm.skip_file
#              break
#            end
#            #
#              
#          end
#        }
#      end
#      #
      
      return line_diffs
    end
    
    class DiffLineManager
      attr_reader :diff, :modified_expect_line, :original_expect_line, :actual_line, :line_num
      attr_accessor :skip_file, :skip_line
      
      def initialize(line_num, expect_line, actual_line)
        @line_num = line_num
        @modified_expect_line = expect_line
        @original_expect_line = expect_line
        @actual_line = actual_line
        
        @diff = diff_line(expect_line, actual_line)
        @diff_idx = 0
        
        @skip_file = false
        @skip_line = false
      end
      
      def get_next
        if @diff_idx < @diff.length
          d = @diff[@diff_idx]
          @diff_idx += 1
          return d
        else
          return nil
        end
      end
      
      def delete?
        @diff[@diff_idx][3] == :delete
      end
      
      def original_expect_section
        original_expect_line # TODO
      end
      
      def modified_expect_section
        modified_expect_line # TODO
      end
      
      def ignore
        @diff.delete(@diff_idx)
        @diff_idx -= 1
        if @diff_idx < 0
          @diff_idx = 0
        end
      end
      
      def in_col
        @diff[@diff_idx][0]
      end
      
      def out_col
        @diff[@diff_idx][1]
      end
      
      def chunk
        @diff[@diff_idx][2]
      end
            
      def delete
        @modified_expect_line = @modified_expect_line[0..in_col]+@modified_expect_line[(@modified_expect_line.length-out_col-in_col)..@modified_expect_line.length]
        ignore
      end
      
      def add
        replace(chunk())
      end
      
      def replace(replace_with)
        @modified_expect_line = @modified_expect_line[0..in_col]+replace_with+@modified_expect_line[(@modified_expect_line.length-out_col-in_col)..@modified_expect_line.length]
        ignore
      end
      
      def matches?(replacement_chunk)
        return _compare_line(@modified_expect_line[in_col..out_col], replacement_chunk)
      end
      
      protected
      
      def diff_line(str_a, str_b)
        if str_a == str_b
          []
        elsif str_a.length>str_b.length
          _diff_line(str_b, str_a, :delete, :insert)
        else
          _diff_line(str_a, str_b, :insert, :delete)
        end
      end

      def _diff_line(str_a, str_b, a_type, b_type) # str_b is longer
        in_a = out_a = in_b = out_b = 0
        match = last_match = true
        diff = []
        while out_a < str_a.length and out_b < str_b.length
          match = ( str_a[out_a] == str_b[out_b] )
          if match
            out_b += 1
          end
          out_a += 1
          if last_match!=match
            in_a = out_a
            in_b = out_b
            diff.push([in_a, in_b, str_b[in_b..out_b], b_type])
          end
          last_match = match
        end
        if out_a < str_a.length
          # remaining characters of str_b are missing from str_a
          diff.push([in_a, in_b, str_b[in_b...str_b.length], b_type])
        elsif out_b < str_b.length
          # remaining characters of str_a are missing from str_b
          diff.push([in_a, in_b, str_a[in_a...str_a.length], b_type])
        end
        if diff.empty?
          diff.push([0, 0, str_a[0, str_a.length], b_type])
        end
        diff
      end
    
    end # end class DiffLineManager

    def _diff_lcs( expectation, result )
      #Build the LCS tables
      common = Array.new( expectation.length+1 ).map! {|item| Array.new( result.length+1 ) }
      lcslen = Array.new( expectation.length+1 ).map! {|item| Array.new( result.length+1, 0 ) }
      expectation.each_index do |a|
        result.each_index do |b|
          common[a+1][b+1]= _compare_line( expectation[a], result[b] )
          lcslen[a+1][b+1] = ( common[a+1][b+1] ? lcslen[a][b] + 1 : [ lcslen[a][b-1], lcslen[a-1][b] ].max )
        end
      end

      # Transverse those tables to build the diff
      cursor = {:a=>expectation.length,:b=>result.length}
      diff = [];
      while cursor.values.max > 0
        case
        when cursor[:a]>0 && cursor[:b]>0 && common[cursor[:a]][cursor[:b]]
          # store token, chunk and line
          diff.unshift [:equals,result[cursor[:b]-1],cursor[:b]]
          cursor[:a]-=1 # Move left
          cursor[:b]-=1 # Move up
        when cursor[:b]>0 && (cursor[:a].zero? || lcslen[cursor[:a]][cursor[:b]-1] >= lcslen[cursor[:a]-1][cursor[:b]])
          diff.unshift [:insert,expectation[cursor[:b]-1],cursor[:b],result[cursor[:b]-1]]
          cursor[:b]-=1 # Move up
        when cursor[:a]>0 && (cursor[:b].zero? || lcslen[cursor[:a]][cursor[:b]-1] < lcslen[cursor[:a]-1][cursor[:b]])
          diff.unshift [:delete,expectation[cursor[:a]-1],cursor[:a],result[cursor[:b]-1]]
          cursor[:a]-=1 # Move left
        end
      end
      diff
    end

    def _reduce_noise( diff )
      return diff if diff.length.zero?

      ret = []
      cache = Hash.new{|h,k|h[k]=[]}

      diff.each do |token|
        case token[0]
        when :equals
          [:insert,:delete].each do |action|
            (cache.delete(action)||[]).each do |token|
              ret.push token
            end
          end
          ret.push token
        else
          #puts %Q{pushing: [#{token[0]}] #{token.inspect}}
          cache[token[0]].push token
        end
      end
      ret
    end

    # if the first diff has a bunch of deletes at the end that match inserts at the beginning of the second diff
    # or inserts in at the tail of the 1st that match deletes at the head of the 2nd, 
    def _concatenate_diffs( first_half, second_half )

    end

    def _compare_lines( expectation, result )
      return false unless expectation.length == result.length
      expectation.zip(result).each do |ex, re|
        return false unless _compare_line( ex, re )
      end
      return true
    end

    def _tokenize(ary,token,line=0)
      ary.map do |item|
        [token,item,line+=1]
      end
    end

    def _common_prefix( expectation, result )
      prefix = []
      k=0
      while k < expectation.length
        return prefix if !_compare_line( expectation[k], result[k] )
        prefix.push result[k]
        k+=1
      end
      prefix
    end

    def _common_suffix expectation, result
      _common_prefix( expectation.reverse, result.reverse ).reverse
    end
    
  end

  class Exact < BaseDiff
    def _compare_line( expectation, result )
      expectation == result or expectation.rstrip.chomp == (result!=nil and result.rstrip.chomp)
    end
  end

  class RegExp < BaseDiff
    def _compare_line( expectation, result )
      begin
        expectation = Iconv.conv('UTF-8//IGNORE', 'UTF-8', expectation)
        result = Iconv.conv('UTF-8//IGNORE', 'UTF-8', result)
        
        r = Regexp.new(%Q{\\A#{expectation}\\Z})
        begin
          r.match(result) or (result!=nil and r.match(result.rstrip.chomp))
        rescue
          puts $! # TODO include in pftt bug
          return false
        end
      rescue
        puts $! # TODO include in bork
        return false
      end
    end
  end

  class Formatted < RegExp
    # Provide some setup for inheritance. Really I should come up with a way
    # to abstract this, but yet another implementation will have to work for now.
    class << self
      def patterns arg=nil
        case when arg.nil? #getting with inheritance
          compiled = {}
          ancestors.to_a.reverse_each do |ancestor|
            next true unless ancestor.respond_to? :patterns
            compiled.merge! ancestor.patterns(false)
          end
          compiled
        when arg==false # getting without inheritance
          @patterns ||= {}
        else # setting
          (@patterns||={}).merge! arg
        end
      end
    end
    def patterns arg={}
      (@patterns ||= {}).merge! arg
      self.class.patterns.merge( @patterns )
    end

    protected

    #ok, now for the implementation:
    def _compare_line( expectation, result )
      if expectation == nil || result == nil
        return false
      end
      rresult = result.rstrip.chomp
      expectation = expectation.rstrip.chomp
      if expectation == result or expectation == rresult
        return true
      else
        rex = Regexp.escape(expectation)
      
        # arrange the patterns in longest-to shortest and apply them.
        # the order matters because %string% must be replaced before %s.
        patterns(rex)
      
        super( rex, result ) or super( rex, rresult )
      end
    end

    # and some default patterns
    # see run-tests.php line 1871
    def patterns(rex)
      rex.gsub!('%e', '.+') #[\\\\|/]',
      rex.gsub!('%s', '.+') # TODO use platform specific EOL @host.EOL
      # host.eol_escaped 
      rex.gsub!('%S', '[^\r\n]*')
      rex.gsub!('%a', '.+')
      rex.gsub!('%A', '.*')
      rex.gsub!('%w', '\s*')
      rex.gsub!('%i', '[+-]?\d+')
      rex.gsub!('%d', '\d+')
      rex.gsub!('%x', '[0-9a-fA-F]+')
      rex.gsub!('%f', '[+-]?\.?\d+\.?\d*(?:[Ee][+-]?\d+)?')
      rex.gsub!('%c', '.')
    end

    def show_expect_info
      puts '%e => [\\\\|/]        %s => .+'
      puts '%S => [^\r\n]*        %a => .+'
      puts '%A => .*              %w => \s*'
      puts '%i => [+-]?\d+        %d => \d+'
      puts '%x => [0-9a-fA-F]+    %f => [+-]?\.?\d+\.?\d*(?:[Ee][+-]?\d+)?'
      puts '%c => .'
    end

    class Php5 < Formatted
      def patterns(rex)
        rex.gsub!('%unicode_string_optional%', 'string') #PHP6+: 'Unicode string'
        rex.gsub!('%binary_string_optional%', 'string') #PHP6+: 'binary_string'
        rex.gsub!('%unicode\|string%', 'string') #PHP6+: 'unicode'
        rex.gsub!('%string\|unicode%', 'string') #PHP6+: 'unicode'
        rex.gsub!('%u\|b%', '')
        rex.gsub!('%b\|%u', '') #PHP6+: 'u'
        super(rex)
      end
      
      def show_expect_info
        super
        
        puts 'PHP5'
        puts '%u\|b%                   => \'\'      %b\|%u                    => \'\''
        puts '%binary_string_optional% => string    %unicode_string_optional% => string'
        puts '%unicode\|string%        => string    %string\|unicode%         =>  string'
      end
    end # end Php5
    
    class Php6 < Formatted
      def patterns(rex)
        rex.gsub!('%unicode_string_optional%', 'Unicode string') #PHP6+: 'Unicode string'
        rex.gsub!('%binary_string_optional%', 'binary_string') #PHP6+: 'binary_string'
        rex.gsub!('%unicode\|string%', 'unicode') #PHP6+: 'unicode'
        rex.gsub!('%string\|unicode%', 'unicode') #PHP6+: 'unicode'
        rex.gsub!('%u\|b%', 'u')
        rex.gsub!('%b\|%u', 'u') #PHP6+: 'u'
        super(rex)
      end
      
      def show_expect_info
        super
        
        puts 'PHP6'
        puts '%u\|b%            => u          %b\|%u            => u'
        puts '%unicode\|string% => unicode    %string\|unicode% => unicode'
        puts '%binary_string_optional%  => binary_string'
        puts '%unicode_string_optional% => Unicode string'
      end
    end # end Php6
    
  end
end
end